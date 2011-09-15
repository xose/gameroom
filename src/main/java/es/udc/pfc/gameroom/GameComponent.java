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

import java.util.HashMap;
import java.util.Map;

import org.xmpp.component.AbstractComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import es.udc.pfc.gameroom.rooms.ChessRoom;
import es.udc.pfc.gameroom.rooms.Room;

public class GameComponent extends AbstractComponent {

	private final Map<String, Room> rooms;

	public GameComponent() {
		rooms = new HashMap<String, Room>();
	}

	@Override
	public String getName() {
		return "Games";
	}

	@Override
	public String getDescription() {
		return "Game Component";
	}

	private final String getMUCServiceName() {
		// TODO: Turn this into a configuration option
		return "conference." + compMan.getServerName();
	}

	private final String getUniqueRoomName() {
		final IQ unique = new IQ(IQ.Type.get);
		unique.setFrom(getJID());
		unique.setTo(getMUCServiceName());
		unique.setChildElement("unique", "http://jabber.org/protocol/muc#unique");

		try {
			final IQ result = compMan.query(this, unique, 1000);

			if (result.getType() != IQ.Type.result || !result.getChildElement().getName().equals("unique"))
				return null;

			return result.getChildElement().getText();
		} catch (final ComponentException e) {
			return null;
		}
	}

	public final Room newRoom(final String type) {
		final String roomID = getUniqueRoomName();
		if (roomID == null) {
			log.error("Error requesting unique room name");
			return null;
		}

		final Room newRoom = new ChessRoom(this, new JID(roomID, getMUCServiceName(), null));
		newRoom.joinRoom();
		newRoom.configureRoom();
		newRoom.changeSubject("Chess Game");

		rooms.put(roomID, newRoom);

		return newRoom;
	}

	@Override
	protected void handleMessage(final Message message) {
		if (message.getBody() == null)
			return;

		final JID from = message.getFrom();
		if (from.getDomain().equals(getMUCServiceName())) {
			final Room room = rooms.get(from.getNode());

			if (room == null) {
				log.debug(String.format("Room %s not found", from.getNode()));
				return;
			}

			if (message.getType() == Message.Type.groupchat) {
				room.messageReceived(from.getResource(), message.getBody());
			} else if (message.getType() == Message.Type.chat) {
				room.privateMessageRecieved(from.getResource(), message.getBody());
			}
		} else if (message.getBody().equals("play")) {
			for (final Room room : rooms.values()) {
				if (room.isFull()) {
					continue;
				}

				System.out.println("join game");
				room.sendInvitation(from, "Join game");
				return;
			}

			// Create a new room and invite the user
			final Room newRoom = newRoom("chess");
			if (newRoom != null) {
				System.out.println("new game");
				newRoom.sendInvitation(from, "New Game");
			}
		}
	}

	@Override
	protected void handlePresence(final Presence presence) {
		log.debug("Presencia: " + presence.toXML());

		final JID from = presence.getFrom();
		if (from.getDomain().equals(getMUCServiceName())) {
			// Room presence
			final String resource = from.getResource();
			if (resource == null || resource.equals("arbiter"))
				return;

			final String node = from.getNode();

			final Room room = rooms.get(node);
			if (room == null) {
				log.debug(String.format("Room '%s' not found", from.getNode()));
				return;
			}

			if (presence.getType() == null /* available */) {
				room.occupantJoined(resource);
				// TODO: Get full JID from x.item[jid] ?
			} else if (presence.getType() == Presence.Type.unavailable) {
				room.occupantLeft(resource);
				if (room.numOccupants() == 0) {
					room.leaveRoom();
					rooms.remove(node);
				}
			}
		}
	}

	@Override
	public void postComponentStart() {
		log.info("GameRoom initialized");
	}

	@Override
	public void preComponentShutdown() {
		log.info("Shutting down...");

		for (final Room room : rooms.values()) {
			room.leaveRoom();
		}
	}

	@Override
	public void send(final Packet packet) {
		super.send(packet);
	}

}
