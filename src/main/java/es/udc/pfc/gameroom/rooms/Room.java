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

import org.xmpp.packet.JID;

public interface Room {

	public JID getJID();

	public boolean isFull();

	public void joinRoom();

	public void leaveRoom();

	public void configureRoom();

	public void changeSubject(String newSubject);

	public void sendInvitation(JID user, String body);

	public int numOccupants();

	public void occupantJoined(String nickname);

	public void occupantLeft(String nickname);

	public void messageReceived(String nickname, String body);

	public void privateMessageRecieved(String nickname, String body);

}
