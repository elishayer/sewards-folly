package org.ggp.base.player.gamer.statemachine.weightedHeuristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

public final class weightedHeuristic extends StateMachineGamer
{
	static long SEARCH_TIME = 1500;
	float max_moves;
	float opp_max_moves;
	List<Double> weights = Arrays.asList(0.333, 0.333, 0.333);
	List<Double> currWeights;
	int maxMetaReward = 0;
	Random r = new Random();

	@Override
	public String getName() {
		return "SewardsFollyGoalWeightedHeuristic";
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
		while(System.currentTimeMillis() + SEARCH_TIME < timeout) {
			simulateGame();
		}
		currWeights = weights;
		System.out.println("final currweights" + currWeights.toString());

    }

	private void simulateGame() throws TransitionDefinitionException, GoalDefinitionException, MoveDefinitionException {
		double weight1 = r.nextDouble();
		double weight2 = (1 - weight1) * r.nextDouble();
		double weight3 = 1 - weight1 - weight2;
		currWeights = Arrays.asList(weight1, weight2, weight3);
		System.out.println("currweights" + currWeights.toString());

		Role role = getRole();
    	StateMachine machine = getStateMachine();
    	MachineState state = machine.getInitialState();
		while(!machine.findTerminalp(state)) {
			List<Move> actions = new ArrayList<Move>();
			List<Role> roles = machine.getRoles();
			for (int i = 0; i < roles.size(); i++) {
				if (roles.get(i).equals(role)) {
					actions.add(stateMachineSelectMove(System.currentTimeMillis() + 20));
				} else {
					List<Move> legals = machine.findLegals(roles.get(i), state);
					actions.add(legals.get(r.nextInt(legals.size())));
				}
			}
			state = machine.getNextState(state, actions);
		}
		if (machine.findReward(role, state) > maxMetaReward) {
			maxMetaReward = machine.findReward(role, state);
			weights = currWeights;
		}
	}

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
    	max_moves = 0;
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
    	if(actions.size() > max_moves) {
    		max_moves = actions.size();
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
    	if(actions.size() > max_moves) {
    		max_moves = actions.size();
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
    		List<Float> values = new ArrayList<Float>();

    		values.add(machine.findLegals(role, state).size()/max_moves);
    		values.add(1 - (numOppMoves(machine, roles, role, state)/ opp_max_moves));
    		values.add((float)machine.findReward(role, state));
    		float score = 0;
    		for(int i = 0; i < currWeights.size(); i++) {
    			score += currWeights.get(i) * values.get(i);
    		}
    		return score;

    	}

    	List<Move> actions = machine.findLegals(role, state);
    	if(numOppMoves(machine, roles, role, state) > opp_max_moves) {
    		opp_max_moves = numOppMoves(machine, roles, role, state);
    	}
    	if(actions.size() > max_moves) {
    		max_moves = actions.size();
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