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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.muc.Invitation;
import org.xmpp.muc.JoinRoom;
import org.xmpp.muc.LeaveRoom;
import org.xmpp.muc.RoomConfiguration;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class AbstractRoom implements Room {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	abstract protected String getXMLNS();
	abstract protected void updateSubject();
	abstract protected void playerJoined(JID user);
	abstract protected void playerLeft(JID user);
	abstract protected void commandReceived(JID user, Element x) throws Exception;

	private final GameComponent component;
	private final JID roomJID;
	private final JID arbiterJID;
	
	protected final List<JID> players;

	protected AbstractRoom(final GameComponent component, final JID roomJID) {
		this.component = checkNotNull(component);
		this.roomJID = checkNotNull(roomJID);
		this.arbiterJID = new JID(roomJID.getNode(), roomJID.getDomain(), "arbiter");
		this.players = Lists.newArrayList();
	}

	@Override
	public final JID getJID() {
		return roomJID;
	}

	@Override
	public void joinRoom() {
		send(new JoinRoom(component.getJID(), arbiterJID));
	}

	@Override
	public void leaveRoom() {
		send(new LeaveRoom(component.getJID(), arbiterJID));
	}

	@Override
	public void configureRoom() {
		final Map<String, Collection<String>> fields = Maps.newHashMap();
		final Collection<String> no = ImmutableList.of("0");
		final Collection<String> si = ImmutableList.of("1");

		fields.put("muc#roomconfig_persistentroom", no);
		fields.put("muc#roomconfig_publicroom", no);
		fields.put("muc#roomconfig_membersonly", si);
		fields.put("muc#roomconfig_changesubject", no);

		final RoomConfiguration config = new RoomConfiguration(fields);
		config.setFrom(component.getJID());
		config.setTo(roomJID);

		send(config);
		
		updateSubject();
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
		final Invitation invite = new Invitation(user, body);
		invite.setFrom(component.getJID());
		invite.setTo(roomJID);

		send(invite);
	}

	@Override
	public void messageReceived(final Message msg) {

	}

	@Override
	public void privateMessageRecieved(final Message message) {
		final Element x = message.getChildElement("x", getXMLNS());
		if (x == null)
			return;

		if (x.element("ping") != null) {
			final Message msg = new Message();
			msg.addChildElement("x", getXMLNS()).addElement("pong");
			sendMessage(message.getFrom(), msg);
			return;
		}

		try {
			commandReceived(message.getFrom(), x);
		} catch (Exception e) {
			final Message msg = new Message();
			msg.addChildElement("x", getXMLNS()).addElement("error").addAttribute("status", e.getMessage());
			sendMessage(message.getFrom(), msg);
		}
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

	protected final void send(final Packet packet) {
		component.send(packet);
	}
}
