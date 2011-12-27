package es.udc.pfc.gameroom;

import org.xmpp.packet.JID;

import es.udc.pfc.gamelib.chess.MiniChessGame;

public final class MiniChessRoom extends ChessRoom {

	public MiniChessRoom(final GameComponent component, final JID roomJID) {
		super(component, roomJID, new MiniChessGame());
	}
	
	@Override
	public final String getType() {
		return "minichess";
	}
	
	@Override
	protected final void updateSubject() {
		changeSubject("MiniChess");
	}
}
