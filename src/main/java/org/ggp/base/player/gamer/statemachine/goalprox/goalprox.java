package org.ggp.base.player.gamer.statemachine.goalprox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public final class goalprox extends StateMachineGamer
{
	static long SEARCH_TIME = 1500;
	MachineState bestTerminal;
	int bestTerminalScore;

	@Override
	public String getName() {
		return "SewardsFollyGoalProximity";
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
		bestTerminalScore = 0;
		float find_terminal_time  = 4 * (timeout - System.currentTimeMillis()) / 5;

		while(timeout - System.currentTimeMillis() > find_terminal_time) {
			searchForTerminal(getStateMachine(), getCurrentState(), getRole());
		}
    }

	private void searchForTerminal(StateMachine machine, MachineState state, Role role) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
	{
		if(machine.findTerminalp(state)) {
			if(machine.findReward(role, state) > bestTerminalScore || bestTerminal == null) {
				bestTerminalScore = machine.findReward(role, state);
				bestTerminal = state;
			}
			return;
		}
		List<Move> actions = new ArrayList<Move>();
		List<Role> roles = machine.getRoles();
		for (int i = 0; i < roles.size(); i++) {
			List<Move> legals = machine.findLegals(roles.get(i), state);
			actions.add(legals.get(new Random().nextInt(legals.size())));
		}
		searchForTerminal(machine, machine.findNext(actions, state), role);
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

		Move best = bestMove(machine, state, roles, role, timeout);

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, best, stop - start));
        return best;
    }

    private Move bestMove(StateMachine machine, MachineState state, List<Role> roles, Role role, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<Move> actions = machine.findLegals(role, state);

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
    	if (machine.findTerminalp(state)) {
    		return machine.findReward(role, state);
    	}

    	if(timeout - System.currentTimeMillis() < SEARCH_TIME) {

    		Set<GdlSentence> contents = state.getContents();
    		Set<GdlSentence> bestContents = bestTerminal.getContents();

    		Set<GdlSentence> intersection = new HashSet<GdlSentence>(contents);
    		intersection.retainAll(bestContents);

    		//return intersection.size() / (contents.size() + bestContents.size() - intersection.size());

    		//return reward of nonterminal state
    		return machine.findReward(role, state);
    	}

    	List<Move> actions = machine.findLegals(role, state);

    	for (int i = 0; i < actions.size(); i++) {
    		float result = minScore(machine, state, roles, role, actions.get(i), alpha, beta, timeout);
    		alpha = Math.max(alpha, result);
    		if (alpha >= beta) {
    			return beta;
    		}
    	}
    	return alpha;
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