/**
 * Copyright 2011 José Martínez
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package es.udc.pfc.gameroom;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bson.BSONObject;
import org.bson.types.BasicBSONList;
import org.bson.types.ObjectId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import es.udc.pfc.xmpp.stanza.IQ;
import es.udc.pfc.xmpp.stanza.JID;
import es.udc.pfc.xmpp.stanza.Message;
import es.udc.pfc.xmpp.stanza.Presence;
import es.udc.pfc.xmpp.stanza.Stanza;
import es.udc.pfc.xmpp.stanza.XMPPNamespaces;
import es.udc.pfc.xmpp.xml.XMLElement;

public abstract class AbstractRoom implements Room {
	
	protected final Logger log = Logger.getLogger(getClass().getSimpleName());
	
	abstract protected String getXMLNS();
	abstract protected void updateSubject();
	abstract protected void playerJoined(JID user);
	abstract protected void playerLeft(JID user);
	abstract protected void commandReceived(JID user, XMLElement x) throws Exception;

	private final GameComponent component;
	private final ObjectId dbId;
	
	private final Date startTime;
	
	private final JID roomJID;
	private final JID arbiterJID;
	
	protected final List<JID> players;

	protected AbstractRoom(final GameComponent component, final JID roomJID) {
		this.component = checkNotNull(component);
		this.roomJID = checkNotNull(roomJID);
		this.arbiterJID = JID.jid(roomJID.getDomain(), roomJID.getNode(), "arbiter");
		this.players = Lists.newArrayList();
		this.dbId = ObjectId.get();
		this.startTime = new Date();
	}
	
	protected AbstractRoom(final GameComponent component, final BSONObject dbObject) {
		this.component = checkNotNull(component);
		this.roomJID = JID.jid(dbObject.get("room").toString());
		this.arbiterJID = JID.jid(roomJID.getDomain(), roomJID.getNode(), "arbiter");
		this.players = Lists.newArrayList();
		this.dbId = (ObjectId)dbObject.get("_id");
		this.startTime = (Date)dbObject.get("started");
		
		final List<Object> playerList = (List<Object>)dbObject.get("players");
		for (final Object player : playerList) {
			players.add(JID.jid(player.toString()));
		}
	}
	
	@Override
	public final JID getJID() {
		return roomJID;
	}

	@Override
	public void joinRoom() {
		final Presence join = new Presence();
		join.setFrom(component.getJID());
		join.setTo(arbiterJID);
		join.addExtension("x", XMPPNamespaces.MUC);
		send(join);
	}

	@Override
	public void leaveRoom() {
		final Presence leave = new Presence(Presence.Type.unavailable);
		leave.setFrom(component.getJID());
		leave.setTo(arbiterJID);
		send(leave);
	}

	@Override
	public ListenableFuture<Void> configureRoom() {
		final SettableFuture<Void> future = SettableFuture.create();
		final Map<String, List<String>> fields = Maps.newHashMap();
		final List<String> no = ImmutableList.of("0");
		final List<String> si = ImmutableList.of("1");

		fields.put("muc#roomconfig_persistentroom", no);
		fields.put("muc#roomconfig_publicroom", no);
		fields.put("muc#roomconfig_membersonly", si);
		fields.put("muc#roomconfig_changesubject", no);

		final IQ config = new IQ(IQ.Type.set);
		config.setFrom(component.getJID());
		config.setTo(roomJID);
		final XMLElement data = config.addExtension("x", XMPPNamespaces.DATA);
		data.setAttribute("type", "submit");
		
		for (final Map.Entry<String, List<String>> field : fields.entrySet()) {
			final XMLElement f = data.addChild("field");
			f.setAttribute("var", field.getKey());
			for (final String value : field.getValue()) {
				f.addChild("value").setText(value);
			}
		}

		Futures.addCallback(component.sendIQ(config), new FutureCallback<IQ>() {
			@Override
			public void onSuccess(IQ result) {
				updateSubject();
				future.set(null);
			}

			@Override
			public void onFailure(Throwable t) {
				future.setException(t);
			}
		});
		
		return future;
	}

	protected void changeSubject(final String newSubject) {
		final Message m = new Message();
		m.setFrom(component.getJID());
		m.setTo(roomJID);

		m.setType(Message.Type.groupchat);
		m.setSubject(newSubject);

		send(m);
	}

	@Override
	public int numPlayers() {
		return players.size();
	}

	@Override
	public void occupantJoined(final JID user) {
		if (!joinable() || players.contains(user)) 
			return;
		
		players.add(user);
		log.info(user.toString() + " joined");
		
		playerJoined(user);
	}

	@Override
	public void occupantLeft(final JID user) {
		if (!players.contains(user))
			return;
		
		playerLeft(user);
		
		log.info(user.toString() + " left");
		players.remove(user);
	}

	@Override
	public void sendInvitation(final JID user, final String body) {
		final Message invite = new Message();
		invite.setFrom(component.getJID());
		invite.setTo(roomJID);
		
		final XMLElement inv = invite.addExtension("x", XMPPNamespaces.MUC_USER).addChild("invite");
		inv.setAttribute("to", user.toString());
		inv.setChildText("reason", body);

		send(invite);
	}

	@Override
	public void messageReceived(final Message msg) {

	}

	@Override
	public void privateMessageRecieved(final Message message) {
		final XMLElement x = message.getExtension("x", getXMLNS());
		if (x == null)
			return;

		if (x.hasChild("ping")) {
			final Message msg = new Message();
			msg.addExtension("x", getXMLNS()).addChild("pong");
			sendMessage(message.getFrom(), msg);
			return;
		}

		try {
			commandReceived(message.getFrom(), x);
		} catch (Exception e) {
			final Message msg = new Message();
			msg.addExtension("x", getXMLNS()).addChild("error").setAttribute("status", e.getMessage());
			sendMessage(message.getFrom(), msg);
		}
	}
	
	protected abstract void buildBSONObject(final BSONObject data);
	
	protected final void saveDBObject() {
		final DBObject result = new BasicDBObject("_id", dbId);
		
		result.put("room", getJID().toString());
		result.put("type", getType());
		result.put("started", startTime);
		result.put("lastUpdate", new Date());
		
		final List<Object> playerList = new BasicBSONList();
		for (final JID player : players) {
			playerList.add(player.toString());
		}
		result.put("players", playerList);
		
		buildBSONObject(result);
		Database.saveGame(result);
		System.out.println("saved: " + result.toString());
	}
	
	protected final void sendGroupMessage(final Message message) {
		sendMessage(getJID(), message);
	}
	
	protected final void sendMessage(final JID user, final Message message) {
		message.setType(user.getResource() != null ? Message.Type.chat : Message.Type.groupchat);
		message.setFrom(component.getJID());
		message.setTo(user);

		send(message);
	}

	protected final void send(final Stanza stanza) {
		component.send(stanza);
	}
}
