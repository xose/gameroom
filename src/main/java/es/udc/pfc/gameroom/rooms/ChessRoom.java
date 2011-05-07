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

import es.udc.pfc.gamelib.board.Position;
import es.udc.pfc.gamelib.chess.ChessGame;
import es.udc.pfc.gameroom.GameComponent;

public class ChessRoom extends AbstractRoom {

	private final ChessGame chessGame;

	public ChessRoom(final GameComponent component, final JID roomJID) {
		super(component, roomJID);
		chessGame = new ChessGame(ChessGame.ChessType.MiniChess);
	}

	@Override
	public boolean isFull() {
		return chessGame.getPlayerCount() == 2;
	}

	@Override
	protected final void commandReceived(final String nickname, final String[] command) {
		if (command.length == 1 && command[0].equals("!board")) {
			sendMessage(chessGame.getBoard().toString(), nickname);
		} else if (command.length == 3 && command[0].equals("!move")) {
			if (!nickname.equals(chessGame.getCurrentPlayer().getName())) {
				sendMessage("Invalid turn", nickname);
				return;
			}

			final Position from = Position.fromString(command[1]);
			final Position to = Position.fromString(command[2]);

			if (from == null || to == null) {
				sendMessage("Invalid position", nickname);
				return;
			}

			if (chessGame.move(from, to)) {
				sendMessage(String.format("move %s %s", from.toString(), to.toString()));

				// TODO: check status
			} else {
				sendMessage("Invalid movement", nickname);
			}
		}
	}

	@Override
	protected void playerJoined(final String nickname) {
		log.debug(nickname + " joined");
		chessGame.addPlayer(nickname);
	}

	@Override
	protected void playerLeft(final String nickname) {

	}

}
