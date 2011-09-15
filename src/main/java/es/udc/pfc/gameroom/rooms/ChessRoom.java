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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;

import es.udc.pfc.gamelib.PlayerAddedEvent;
import es.udc.pfc.gamelib.board.PieceMovedEvent;
import es.udc.pfc.gamelib.board.Position;
import es.udc.pfc.gamelib.chess.ChessBoard;
import es.udc.pfc.gamelib.chess.ChessGame;
import es.udc.pfc.gamelib.chess.ChessMovement;
import es.udc.pfc.gamelib.chess.ChessPlayer;
import es.udc.pfc.gamelib.chess.MiniChessRules;
import es.udc.pfc.gameroom.GameComponent;

public class ChessRoom extends AbstractRoom {
	
	private static final Joiner cmdJoiner = Joiner.on(':');

	private final ChessGame chessGame;

	public ChessRoom(final GameComponent component, final JID roomJID) {
		super(component, roomJID);
		chessGame = new ChessGame(ChessBoard.fromString(ChessBoard.CHESSBOARD_MINI), new MiniChessRules());
		chessGame.addListener(this);
	}

	@Override
	public boolean isFull() {
		return chessGame.getPlayerCount() == 2;
	}

	@Override
	protected final void commandReceived(final String nickname, final ImmutableList<String> command) {
		if (command.size() == 1 && command.get(0).equals("!board")) {
			sendMessage(cmdJoiner.join("board", chessGame.getBoard()), nickname);
		} else if (command.size() == 3 && command.get(0).equals("!move")) {
			if (!nickname.equals(chessGame.getCurrentPlayer().getName())) {
				sendMessage("Invalid turn", nickname);
				return;
			}

			final Position from = Position.fromString(command.get(1));
			final Position to = Position.fromString(command.get(2));

			if (from == null || to == null) {
				sendMessage("Invalid position", nickname);
				return;
			}

			try {
				chessGame.movePiece(from, to);
			} catch (Exception e) {
				sendMessage(e.getMessage(), nickname);
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
	
	@Subscribe
	public void playerAdded(final PlayerAddedEvent<ChessPlayer> event) {
		ChessPlayer player = event.getPlayer();
		sendMessage(cmdJoiner.join("color", player.getColor().name()), player.getName());
	}
	
	@Subscribe
	public void pieceMoved(final PieceMovedEvent<ChessMovement> event) {
		ChessMovement move = event.getMovement();
		sendMessage(cmdJoiner.join("move", move.getFrom(), move.getTo()));
		
	}

}
