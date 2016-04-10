package org.ggp.base.player.gamer.statemachine.minimax;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public final class MinimaxGamer extends StateMachineGamer
{
	@Override
	public String getName() {
		return "SewardsFollyMinimax";
	}

	@Override
    public StateMachine getInitialStateMachine() {
        return new CachedStateMachine(new ProverStateMachine());
    }

	@Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

	@Override
    public void stateMachineAbort() {
        // Sample gamers do no special cleanup when the match ends abruptly.
    }

    @Override
    public void stateMachineStop() {
        // Sample gamers do no special cleanup when the match ends normally.
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException {
        // Sample gamers do no game previewing.
    }

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		// no metagraming for the minimax gamer
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		long start = System.currentTimeMillis();

		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState());

        // Build the minimax tree
		MachineState state = getCurrentState();
		List<Role> roles = getStateMachine().findRoles();
		Role role = getRole();

		Move best = bestMove(machine, state, roles, role);

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, best, stop - start));
        return best;
    }

    private Move bestMove(StateMachine machine, MachineState state, List<Role> roles, Role role) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<Move> actions = machine.findLegals(role, state);

    	// the best action and score
    	Move action = actions.get(0);
    	int score = 0;

    	for (int i = 0; i < actions.size(); i++) {
    		int result = minScore(machine, state, roles, role, actions.get(i), 0, 100);
    		if (result == 100) {
    			return actions.get(i);
    		}
    		if (result > score) {
    			score = result;
    			action = actions.get(i);
    		}
    	}
    	return action;
    }

    private int minScore(StateMachine machine, MachineState state, List<Role> roles, Role role, Move action, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	// System.out.println("alpha: " + alpha + " | beta: " + beta);
    	Role opponent = getOpponentRole(roles, role);
    	List<Move> actions = machine.findLegals(opponent, state);

    	for (int i = 0; i < actions.size(); i++) {
    		List<Move> move = new ArrayList<Move>();
    		if (role.equals(roles.get(0))) {
    			move.add(action);
    			move.add(actions.get(i));
    		} else {
    			move.add(actions.get(i));
    			move.add(action);
    		}
    		MachineState newState = machine.findNext(move, state);
    		int result = maxScore(machine, roles, role, newState, alpha, beta);
    		beta = Math.min(beta, result);
    		if (beta <= alpha) {
    			return alpha;
    		}
    	}
    	return beta;
    }

    private int maxScore(StateMachine machine, List<Role> roles, Role role, MachineState state, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	// System.out.println("alpha: " + alpha + " | beta: " + beta);
    	if (machine.findTerminalp(state)) {
    		return machine.findReward(role, state);
    	}

    	List<Move> actions = machine.findLegals(role, state);

    	for (int i = 0; i < actions.size(); i++) {
    		int result = minScore(machine, state, roles, role, actions.get(i), alpha, beta);
    		alpha = Math.max(alpha, result);
    		if (alpha >= beta) {
    			return beta;
    		}
    	}
    	return alpha;
    }

    // get the opponent as the player that is not this machine,
    // arbitrary returning the 0th player if none is found that
    // is not this player
    private Role getOpponentRole(List<Role> roles, Role role) {
    	for (int i = 0; i < roles.size(); i++) {
    		if (!roles.get(i).equals(role)) {
    			return roles.get(i);
    		}
    	}
    	return roles.get(0);
    }
}