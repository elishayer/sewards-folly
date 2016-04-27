package org.ggp.base.player.gamer.statemachine.MCTS;

import java.util.ArrayList;
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

public final class MCTS extends StateMachineGamer
{
	public class Node {
		private List<Node> children = null;
		private float score;
		private MachineState state;
		private int visits;
		private Node parent;
		private Move move;

		public Node(MachineState state, float score, Node parent, Move move) {
			this.children = new ArrayList<>();
			this.score = score;
			this.state = state;
			this.visits = 0;
			this.parent = parent;
			this.move = move;
		}

		public void addChild(Node child) {
			children.add(child);
		}

		public void visit() {
			visits += 1;
		}
	}

	static long SEARCH_TIME = 1500;
	static int LEVEL = 4;
	static Random r = new Random();

	private double expansionFactor;
	private int expansionFactorTotal = 0;
	private int expansionFactorNum = 0;
	private double explorationTime;
	private int numCharges;




	@Override
	public String getName() {
		return "SewardsFollyMCTS";
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
		return;
		/*
		int chargesSent = 0;
		double totalChargeTime = 0;
		StateMachine machine = getStateMachine();
		List<Role> roles = machine.findRoles();
		Role role = getRole();
		MachineState state = machine.getInitialState();
		while (System.currentTimeMillis() < timeout - SEARCH_TIME) {
			long start = System.currentTimeMillis();
			depthCharge(machine, roles, role, state, true);
			totalChargeTime += (double) (System.currentTimeMillis() - start);
			chargesSent++;
		}
		double depthLength = totalChargeTime / chargesSent;
		System.out.println("totalchargetime: " + totalChargeTime + " | chargesSent: " + chargesSent + " | depthTime: " + depthLength);
		expansionFactor = expansionFactorTotal / (double) expansionFactorNum;
		double nodesExplored = Math.pow(expansionFactor, LEVEL);
		System.out.println("expansionfactor: " + expansionFactor + " | nodesExplored per move: " + nodesExplored);
		explorationTime = nodesExplored * depthLength;
		System.out.println("exptime: " + explorationTime);
		*/
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		long start = System.currentTimeMillis();
		numCharges = (int) ((timeout - start) / explorationTime);
		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState());

		Node curState = new Node(getCurrentState(), 0, null, null);
		System.out.println("state" );
		while(timeout - System.currentTimeMillis() >= SEARCH_TIME) {
			Node selected = select(curState);
			expand(selected, machine, getRole());
		}

		Move bestMove = null;
		float bestScore = 0;
		for(int i = 0; i < curState.children.size(); i++) {
			System.out.println("score: " + curState.children.get(i).score);
			if(curState.children.get(i).score >= bestScore) {
				bestMove = curState.children.get(i).move;
				bestScore = curState.children.get(i).score;
			}
		}

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
        return bestMove;
    }

    private Node select(Node node) {
    	System.out.println(node.visits);
    	if(node.visits <= 1) return node;

    	for(int i = 0; i < node.children.size(); i++) {
    		if (node.children.get(i).visits == 0 ) {
    			return node.children.get(i);
    		}
    	}
    	float score = 0;
    	Node result = node;

    	for(int i = 0; i < node.children.size(); i++) {
    		float newscore = selectfn(node.children.get(i));
    		if(newscore > score) {
    			score = newscore;
    			result = node.children.get(i);
    		}
    	}
    	return select(result);
    }

    float selectfn(Node node) {
    	return (float) (node.score + Math.sqrt(2 * Math.log(node.parent.visits)/node.visits));
    }

    private void expand(Node node, StateMachine machine, Role role) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	System.out.println("expand");
    	List<Move> actions =  machine.getLegalMoves(node.state, role);
    	for(int i = 0; i< actions.size(); i++) {
    		List<Move> action = new ArrayList<Move>();
    		action.add(actions.get(i));
    		MachineState newstate = machine.getNextState(node.state, action);
    		float totalscore = 0;
    		for(int j = 0; j < 100; j++) {
    			float score = (float) depthCharge(machine, machine.getRoles(), role, newstate, false);
    			totalscore += score;
    		}
    		totalscore /= 100;

    		System.out.println("child added");
    		Node newNode = new Node(newstate, 0, node, actions.get(i));
    		node.children.add(newNode);

    		backpropogate(newNode, totalscore);
    	}
    }

    private void backpropogate(Node node, float score) {
    	node.visit();
    	node.score += score;
    	if(node.parent != null) {
    		backpropogate(node.parent, score);
    	}
    }

    private double monteCarlo(StateMachine machine, List<Role> roles, Role role, MachineState state, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	double total = 0;
    	for (int i = 0; i < numCharges; i++) {
    		if (System.currentTimeMillis() > timeout - SEARCH_TIME) {
    			return total / (i + 1);
    		}
    		double depthVal = depthCharge(machine, roles, role, state, false);
    		total += depthVal;
    	}
    	return total / numCharges;
    }





    private Move bestMove(StateMachine machine, MachineState state, List<Role> roles, Role role, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<Move> actions = machine.findLegals(role, state);

    	// the best action and score
    	Move action = actions.get(0);
    	int score = 0;

    	for (int i = 0; i < actions.size(); i++) {
    		int result = minScore(machine, state, roles, role, actions.get(i), 0, 100, 0, timeout);
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

    private int minScore(StateMachine machine, MachineState state, List<Role> roles, Role role, Move action, int alpha, int beta, int level, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<List<Move> > actions = getActions(roles, role, action, machine, state);

    	for (int i = 0; i < actions.size(); i++) {
    		MachineState newState = machine.findNext(actions.get(i), state);
    		int result = maxScore(machine, roles, role, newState, alpha, beta, level, timeout);
    		beta = Math.min(beta, result);
    		if (beta <= alpha) {
    			return alpha;
    		}
    	}
    	return beta;
    }

    private int maxScore(StateMachine machine, List<Role> roles, Role role, MachineState state, int alpha, int beta, int level, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	if (machine.findTerminalp(state)) {
    		return machine.findReward(role, state);
    	}

    	if (timeout - System.currentTimeMillis() < SEARCH_TIME) {
    		return 0;
    	}

    	if (level > LEVEL) {
    		return (int) monteCarlo(machine, roles, role, state, timeout);
    	}

    	List<Move> actions = machine.findLegals(role, state);

    	for (int i = 0; i < actions.size(); i++) {
    		int result = minScore(machine, state, roles, role, actions.get(i), alpha, beta, level + 1, timeout);
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


    private double depthCharge(StateMachine machine, List<Role> roles, Role role, MachineState state, boolean meta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	if (machine.findTerminalp(state)) {
    		return machine.findReward(role, state);
    	}
    	List<Move> actions = new ArrayList<Move>();
    	for (int i = 0; i < roles.size(); i++) {
    		List<Move> legals = machine.findLegals(roles.get(i), state);
    		if (roles.get(i).equals(role)) {
    			expansionFactorTotal += legals.size();
    			expansionFactorNum++;
    		}
    		actions.add(legals.get(r.nextInt(legals.size())));
    	}
    	state = machine.getNextState(state, actions);
    	return depthCharge(machine, roles, role, state, meta);
    }

}