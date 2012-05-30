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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

import org.bson.BSONObject;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Service.State;

import es.udc.pfc.xmpp.component.AbstractXMPPComponent;
import es.udc.pfc.xmpp.component.ComponentService;
import es.udc.pfc.xmpp.stanza.IQ;
import es.udc.pfc.xmpp.stanza.JID;
import es.udc.pfc.xmpp.stanza.Message;
import es.udc.pfc.xmpp.stanza.Presence;
import es.udc.pfc.xmpp.stanza.XMPPNamespaces;
import es.udc.pfc.xmpp.xml.XMLElement;

public final class GameComponent extends AbstractXMPPComponent {
	
	private final String XMPP_NS = "urn:xmpp:gamepfc";
	
	private final Map<String, Room> rooms;

	public GameComponent() {
		rooms = Maps.newHashMap();
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
		return "conference." + getServerJID().getDomain();
	}

	private final ListenableFuture<String> getUniqueRoomName() {
		final IQ unique = new IQ(IQ.Type.get);
		unique.setFrom(getJID());
		unique.setTo(JID.jid(getMUCServiceName()));
		unique.addExtension("unique", XMPPNamespaces.MUC_UNIQUE);

		return Futures.transform(sendIQ(unique), new AsyncFunction<IQ, String>() {
			@Override
			public ListenableFuture<String> apply(IQ input) throws Exception {
				final XMLElement unique = input.getExtension("unique", XMPPNamespaces.MUC_UNIQUE);
				if (unique == null)
					throw new Exception("No unique received");
				
				return Futures.immediateFuture(unique.getText());
			}
		});
	}

	public final ListenableFuture<Room> newRoom(final String type) {
		final SettableFuture<Room> future = SettableFuture.create();
		Futures.addCallback(getUniqueRoomName(), new FutureCallback<String>() {
			@Override
			public void onSuccess(String roomID) {
				if (roomID == null) {
					log.severe("Error requesting unique room name");
					future.setException(new Exception("Error requesting unique room name"));
					return;
				}
				
				final Room newRoom;

				if (type.equals("minichess")) {
					newRoom = new MiniChessRoom(GameComponent.this, JID.jid(getMUCServiceName(), roomID, null));
				}
				else {
					log.severe("Unknown game type " + type);
					future.setException(new Exception("Unknown game type " + type));
					return;
				}
				
				rooms.put(roomID, newRoom);
				
				newRoom.joinRoom();
				newRoom.configureRoom();
				
				future.set(newRoom);
			}

			@Override
			public void onFailure(Throwable t) {
				future.setException(t);
			}
		});
		
		return future;
	}

	@Override
	protected void handleMessage(final Message message) {
		final JID from = message.getFrom();
		if (from.getDomain().equals(getMUCServiceName())) {
			final Room room = rooms.get(from.getNode());

			if (room == null) {
				log.warning(String.format("Room '%s' not found", from.getNode()));
				return;
			}

			if (message.getType() == Message.Type.groupchat) {
				room.messageReceived(message);
			} else if (message.getType() == Message.Type.chat) {
				room.privateMessageRecieved(message);
			}
		} else {
			final XMLElement play = message.getExtension("play", XMPP_NS);
			if (play == null || !play.hasAttribute("game"))
				return;
			
			for (final Room room : rooms.values()) {
				if (!room.getType().equals(play.getAttribute("game")) || !room.joinable())
					continue;

				log.info("join game: " + room.getJID().toString());
				room.sendInvitation(from, room.getType());
				return;
			}

			// Create a new room and invite the user
			Futures.addCallback(newRoom(play.getAttribute("game")), new FutureCallback<Room>() {
				@Override
				public void onSuccess(Room result) {
					log.info("new game: " + result.getJID().toString());
					result.sendInvitation(from, result.getType());
				}

				@Override
				public void onFailure(Throwable t) {
					log.severe("Could not create a new room: " + t.getMessage());
				}
			});
		}
	}

	@Override
	protected void handlePresence(final Presence presence) {
		final JID from = presence.getFrom();
		if (from.getDomain().equals(getMUCServiceName())) {
			// Room presence
			final String resource = from.getResource();
			if (resource == null || resource.equals("arbiter"))
				return;

			final String node = from.getNode();

			final Room room = rooms.get(node);
			if (room == null) {
				log.warning(String.format("Room '%s' not found", from.getNode()));
				return;
			}

			if (presence.getType() == null /* available */) {
				room.occupantJoined(from);
				// TODO: Get full JID from x.item[jid] ?
			} else if (presence.getType() == Presence.Type.unavailable) {
				room.occupantLeft(from);
				if (room.numPlayers() == 0) {
					room.leaveRoom();
					rooms.remove(node);
					
					log.info("close game: " + room.getJID().toString());
				}
			}
		}
	}
	
	@Override
	public void connected() {
		super.connected();
		
		for (final BSONObject object : Database.getOpenGames()) {
			log.finer("Restarting: " + object.toString());
			
			final String type = object.get("type").toString();
			if ("minichess".equals(type)) {
				final Room newRoom = new MiniChessRoom(this, object);
				
				rooms.put(newRoom.getJID().getNode(), newRoom);
				newRoom.joinRoom();
				
				log.info("Restarted: " + newRoom.getJID());
			}
			else {
				log.warning("Unknown type " + type);
			}
		}
	}

	@Override
	public void willDisconnect() {
		for (final Room room : rooms.values()) {
			room.leaveRoom();
		}
		super.willDisconnect();
	}

	@Override
	protected ListenableFuture<IQ> handleIQ(IQ iq) {
		return Futures.immediateFailedFuture(new Exception("Not implemented"));
	}
	
	public static void main(String[] args) throws IOException {
		
		final ComponentService cs = new ComponentService(new GameComponent(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 5275), "games.localhost", "secret");

		if (cs.startAndWait() != State.RUNNING) {
			System.err.println("Error starting component");
			return;
		}

		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (cs.isRunning()) {
			String line = in.readLine();
			if (line == null || line.isEmpty()) {
				continue;
			}

			if (line.toLowerCase().equals("quit")) {
				cs.stopAndWait();
				break;
			}

			cs.send(line);
		}
	}
	
}
