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

import java.util.Collections;

import org.xmpp.packet.JID;

import com.google.common.collect.ImmutableList;

import es.udc.pfc.gamelib.board.Position;
import es.udc.pfc.gamelib.chess.ChessColor;
import es.udc.pfc.gamelib.chess.ChessGame;
import es.udc.pfc.gamelib.chess.ChessMovement;

public abstract class ChessRoom extends AbstractRoom {
	
	private final ChessGame chessGame;
	
	private boolean joinable;

	protected ChessRoom(final GameComponent component, final JID roomJID, final ChessGame chessGame) {
		super(component, roomJID);
		this.chessGame = checkNotNull(chessGame);
		joinable = true;
	}

	@Override
	public final boolean joinable() {
		return joinable;
	}

	@Override
	protected final void commandReceived(final JID user, final ImmutableList<String> command) throws Exception {
		if (players.size() != 2) 
			throw new Exception("Game is not running");
		
		if (command.size() == 3 && command.get(0).equals("!move")) {
			if (playerColor(user) != chessGame.getCurrentTurn())
				throw new Exception("Invalid turn");

			final Position from = Position.fromString(command.get(1));
			final Position to = Position.fromString(command.get(2));

			if (from == null || to == null)
				throw new Exception("Invalid position");

			final ChessMovement move = chessGame.movePiece(from, to);
			if (move == null)
				throw new Exception("Invalid movement");
			
			sendGroupResponse("move", move.getFrom().toString(), move.getTo().toString());
			
			if (chessGame.isFinished()) {
				sendGroupResponse("finished", chessGame.getWinner() != null ? chessGame.getWinner().name() : "draw");
			}
		}
	}

	@Override
	protected void playerJoined(final JID user) {
		if (players.size() == 2) {
			Collections.shuffle(players);
			sendResponse(players.get(0), "color", ChessColor.WHITE.name());
			sendResponse(players.get(1), "color", ChessColor.BLACK.name());
			joinable = false;
		}
	}

	@Override
	protected void playerLeft(final JID user) {
		final ChessColor left = playerColor(user);
		if (left == null)
			return;
		
		if (players.size() == 2) {
			chessGame.setWinner(left.other());
			sendGroupResponse("finished", chessGame.getWinner().name());
		}
	}
	
	private final ChessColor playerColor(final JID user) {
		if (players.indexOf(user) == 0)
			return ChessColor.WHITE;
		else if (players.indexOf(user) == 1)
			return ChessColor.BLACK;
		
		return null;
	}
	
}
