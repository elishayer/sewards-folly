package org.ggp.base.player.gamer.statemachine.opp_heuristic;

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

public final class opp_heuristic extends StateMachineGamer
{
	static long SEARCH_TIME = 1500;
	float opp_max_moves;

	@Override
	public String getName() {
		return "SewardsFollyOppHeuristic";
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
		opp_max_moves = 0;
    	long start = System.currentTimeMillis();

		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState());

        // Build the minimax tree
		MachineState state = getCurrentState();
		List<Role> roles = getStateMachine().findRoles();
		Role role = getRole();

		Move best = bestMove(machine, state, roles, role, timeout);

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, best, stop - start));
        return best;
    }

    private Move bestMove(StateMachine machine, MachineState state, List<Role> roles, Role role, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<Move> actions = machine.findLegals(role, state);
    	if(numOppMoves(machine, roles, role, state) > opp_max_moves) {
    		opp_max_moves = numOppMoves(machine, roles, role, state);
    	}

    	// the best action and score
    	Move action = actions.get(0);
    	float score = 0;

    	for (int i = 0; i < actions.size(); i++) {
    		float result = minScore(machine, state, roles, role, actions.get(i), 0, 100, timeout);
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

    private float minScore(StateMachine machine, MachineState state, List<Role> roles, Role role, Move action, float alpha, float beta, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<List<Move> > actions = getActions(roles, role, action, machine, state);
    	if(numOppMoves(machine, roles, role, state) > opp_max_moves) {
    		opp_max_moves = numOppMoves(machine, roles, role, state);
    	}

    	for (int i = 0; i < actions.size(); i++) {
    		MachineState newState = machine.findNext(actions.get(i), state);
    		float result = maxScore(machine, roles, role, newState, alpha, beta, timeout);
    		beta = Math.min(beta, result);
    		if (beta <= alpha) {
    			return alpha;
    		}
    	}
    	return beta;
    }

    private float maxScore(StateMachine machine, List<Role> roles, Role role, MachineState state, float alpha, float beta, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	// System.out.println("alpha: " + alpha + " | beta: " + beta);
    	if (machine.findTerminalp(state)) {
    		return machine.findReward(role, state);
    	}

    	if(timeout - System.currentTimeMillis() < SEARCH_TIME) {
    		int opp_moves = numOppMoves(machine, roles, role, state);
    		return 1 - (opp_moves / opp_max_moves);
    	}

    	List<Move> actions = machine.findLegals(role, state);
    	if(numOppMoves(machine, roles, role, state) > opp_max_moves) {
    		opp_max_moves = numOppMoves(machine, roles, role, state);
    	}

    	for (int i = 0; i < actions.size(); i++) {
    		float result = minScore(machine, state, roles, role, actions.get(i), alpha, beta, timeout);
    		alpha = Math.max(alpha, result);
    		if (alpha >= beta) {
    			return beta;
    		}
    	}
    	return alpha;
    }

    private int numOppMoves(StateMachine machine, List<Role> roles, Role role, MachineState state) throws MoveDefinitionException {
		int opp_moves = 0;
    	for(int i = 0; i < roles.size(); i++) {
			if(roles.get(i) != role) {
				opp_moves += machine.findLegals(roles.get(i), state).size();
			}
		}
	    return opp_moves;
    }

    // get a list of all possible permutations of actions
    // by all players (possible more than 2)
    private List<List<Move> > getActions(List<Role> roles, Role role, Move action,
    		StateMachine machine, MachineState state) throws MoveDefinitionException {
    	List<List<Move> > actions = new ArrayList<List<Move> >();
    	List<Move> curr = new ArrayList<Move>();
    	recursiveActionHelper(roles, role, action, machine, state, 0, curr, actions);
    	return actions;
    }

    // recursive action possibility builder
    private void recursiveActionHelper(List<Role> roles, Role role,
    		Move action, StateMachine machine, MachineState state, int i,
    		List<Move> curr, List<List<Move> > actions) throws MoveDefinitionException {
    	if (i == roles.size()) {
    		actions.add(curr);
    		return;
    	} else {
    		if (roles.get(i).equals(role)) {
				List<Move> dest = new ArrayList<Move>(curr);
    			dest.add(action);
    			recursiveActionHelper(roles, role, action, machine, state, i + 1, dest, actions);
    		} else {
    			List<Move> possibleActions = machine.getLegalMoves(state, roles.get(i));
    			for (int j = 0; j < possibleActions.size(); j++) {
    				List<Move> dest = new ArrayList<Move>(curr);
    				dest.add(possibleActions.get(j));
    				recursiveActionHelper(roles, role, action, machine, state, i + 1, dest, actions);
    			}
    		}
    	}
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