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

package es.udc.pfc.gameroom.rooms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.muc.Invitation;
import org.xmpp.muc.JoinRoom;
import org.xmpp.muc.LeaveRoom;
import org.xmpp.muc.RoomConfiguration;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import es.udc.pfc.gameroom.GameComponent;

public abstract class AbstractRoom implements Room {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	abstract protected void playerJoined(String nickname);

	abstract protected void playerLeft(String nickname);

	abstract protected void commandReceived(String nickname, String[] command);

	private final GameComponent component;
	private final JID roomJID;
	private int occupants;

	public AbstractRoom(final GameComponent component, final JID roomJID) {
		this.component = component;
		this.roomJID = roomJID;
		occupants = 0;
	}

	@Override
	public final JID getJID() {
		return roomJID;
	}

	private final JID getOccupantJID(final String nick) {
		return new JID(roomJID.getNode(), roomJID.getDomain(), nick);
	}

	@Override
	public void joinRoom() {
		send(new JoinRoom(component.getJID(), getOccupantJID("arbiter")));
	}

	@Override
	public void leaveRoom() {
		send(new LeaveRoom(component.getJID(), getOccupantJID("arbiter")));
	}

	@Override
	public void configureRoom() {
		final Map<String, Collection<String>> fields = new HashMap<String, Collection<String>>();
		final Collection<String> no = new ArrayList<String>();
		final Collection<String> si = new ArrayList<String>();
		no.add("0");
		si.add("1");

		fields.put("muc#roomconfig_persistentroom", no);
		fields.put("muc#roomconfig_publicroom", no);
		fields.put("muc#roomconfig_membersonly", si);
		fields.put("muc#roomconfig_changesubject", no);

		final RoomConfiguration config = new RoomConfiguration(fields);
		config.setFrom(component.getJID());
		config.setTo(roomJID);

		send(config);
	}

	@Override
	public void changeSubject(final String newSubject) {
		final Message m = new Message();
		m.setFrom(component.getJID());
		m.setTo(roomJID);

		m.setType(Message.Type.groupchat);
		m.setSubject(newSubject);

		send(m);
	}

	@Override
	public int numOccupants() {
		return occupants;
	}

	@Override
	public void occupantJoined(final String nickname) {
		occupants++;
		playerJoined(nickname);
	}

	@Override
	public void occupantLeft(final String nickname) {
		occupants--;
		playerLeft(nickname);
	}

	@Override
	public void sendInvitation(final JID user, final String body) {
		final Invitation invite = new Invitation(user, body);
		invite.setFrom(component.getJID());
		invite.setTo(roomJID);

		send(invite);
	}

	@Override
	public void messageReceived(final String nickname, final String body) {

	}

	@Override
	public void privateMessageRecieved(final String nickname, final String body) {
		final String[] command = body.split(" ");

		if (command.length == 0 || !command[0].startsWith("!"))
			return;

		if (command[0].equals("!ping") && command.length == 1) {
			sendMessage("pong", nickname);
			return;
		}

		commandReceived(nickname, command);
	}

	protected final void sendMessage(final String body) {
		sendMessage(body, null);
	}

	protected final void sendMessage(final String body, final String nickname) {
		final Message msg = new Message();
		msg.setFrom(component.getJID());
		msg.setTo(getOccupantJID(nickname));
		msg.setType(nickname != null ? Message.Type.chat : Message.Type.groupchat);
		msg.setBody(body);

		send(msg);
	}

	protected final void send(final Packet packet) {
		component.send(packet);
	}
}
