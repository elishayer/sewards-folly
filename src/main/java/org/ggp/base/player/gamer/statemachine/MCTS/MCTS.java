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
	/* Node Structure for state tree
	 */
	public class Node {
		private List<Node> children = null;
		private double score;
		private MachineState state;
		private int visits;
		private Node parent;
		private Move move;

		public Node(MachineState state, double score, Node parent, Move move) {
			this.children = new ArrayList<>();
			this.score = score;
			this.state = state;
			this.visits = 0;
			this.parent = parent;
			this.move = move;
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

	private int unexplored;


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
		System.out.println("NEW SELECT");

    	long start = System.currentTimeMillis();
		numCharges = (int) ((timeout - start) / explorationTime);
		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState());

		Node curState = new Node(getCurrentState(), 0, null, null);

		//tracks number of unexplored nodes in the treee
		unexplored = 1;

		//selects and expands on a node until time is up or all nodes have been searched
		while(timeout - System.currentTimeMillis() >= SEARCH_TIME && unexplored > 0) {
			Node selected = select(curState);
			expand(selected, machine, getRole());
			//System.out.println("undexplored " + unexplored);
			//System.out.println("");
		}

		System.out.println(getNodeCount(curState, machine));

		//selects the best move based on the scores of the child nodes
		Move bestMove = null;
		double bestScore = 0;
		for (int i = 0; i < curState.children.size(); i++) {
			System.out.println("score: " + curState.children.get(i).score);
			if (curState.children.get(i).score >= bestScore) {
				bestMove = curState.children.get(i).move;
				bestScore = curState.children.get(i).score;
			}
		}

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
        return bestMove;
    }

    private int getNodeCount(Node node, StateMachine machine) {
    	//System.out.println("score: "+ node.score + " | terminal: " + machine.findTerminalp(node.state) + " | visits " + node.visits);
    	int count = 1;
    	for (int i = 0; i < node.children.size(); i++) {
    	   	count += getNodeCount(node.children.get(i), machine);
    	}
    	return count;
    }


    /* selects a node to expand
     */
    private Node select(Node node) {
    	//don't look further
    	if (node == null || node.visits == 0)	{
    		return node;
    	}

    	//choose unvisited child
    	for (int i = 0; i < node.children.size(); i++) {
    		if (node.children.get(i).visits == 0 ) {
    			return node.children.get(i);
    		}
    	}

    	//choose from visited childs based on scores and visits
    	double score = 0;
    	Node result = null;
    	for (int i = 0; i < node.children.size(); i++) {
    		double newscore = selectfn(node.children.get(i));
    		if (newscore > score) {
    			score = newscore;
    			result = node.children.get(i);
    		}
    	}
    	return select(result);
    }

    double selectfn(Node node) {
    	return (double) (node.score + Math.sqrt(2 * Math.log(node.parent.visits)/node.visits));
    }

    /*
     * expands on a node and gets scores for the node and its new children
     */
    private void expand(Node node, StateMachine machine, Role role) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	//avoid expanding a null node
    	if (node == null) {
    		//System.out.println("null node");
    		return;
    	}

		unexplored--;

    	//System.out.print("term: " + machine.findTerminalp(node.state)); // + " | score" + node.score);

		//if terminal then a MCS is not needed
    	if (machine.findTerminalp(node.state)) {
    		node.visits++;
    		int score = machine.findReward(role, node.state);
    		node.score = score;
    		backpropogate(node.parent, score);
        	//System.out.println(node.parent.score);
    		return;
    	}

    	//run a MCS for each action
    	List<Move> actions = machine.getLegalMoves(node.state, role);
    	for(int i = 0; i< actions.size(); i++) {
    		List<Move> action = new ArrayList<Move>();
    		action.add(actions.get(i));
    		MachineState newstate = machine.getNextState(node.state, action);
    		double totalscore = 0;
    		for(int j = 0; j < 500; j++) { //place holder #
    			double score = (double) depthCharge(machine, machine.getRoles(), role, newstate, false, 0);
    			totalscore += score;
    		}
    		totalscore /= 500;

    		//add nodes to tree
    		Node newNode = new Node(newstate, totalscore, node, actions.get(i));
    		node.children.add(newNode);
    		unexplored++;

    		backpropogate(newNode.parent, totalscore);
    	}

    	//System.out.println(" | score: " + node.score);
    }

    private void backpropogate(Node node, double score) {
    	node.visits++;
    	node.score += score;
    	node.score /= node.visits; //adjust to avoid always looking at the same nodes
    	if(node.parent != null) {
    		backpropogate(node.parent, score);
    	}
    }

    private double depthCharge(StateMachine machine, List<Role> roles, Role role, MachineState state, boolean meta, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	if (machine.findTerminalp(state)) { //stop on terminal state
    		return machine.findReward(role, state);
    	}

    	//choose a random action for each player
    	List<Move> actions = new ArrayList<Move>();
    	for (int i = 0; i < roles.size(); i++) {
    		List<Move> legals = machine.findLegals(roles.get(i), state);
    		if (roles.get(i).equals(role)) {
    			expansionFactorTotal += legals.size();
    			expansionFactorNum++;
    		}
    		actions.add(legals.get(r.nextInt(legals.size())));
    	}

    	//recur
    	state = machine.getNextState(state, actions);
    	return depthCharge(machine, roles, role, state, meta, level + 1);
    }

    /*
    private double monteCarlo(StateMachine machine, List<Role> roles, Role role, MachineState state, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	double total = 0;
    	for (int i = 0; i < numCharges; i++) {
    		if (System.currentTimeMillis() > timeout - SEARCH_TIME) {
    			return total / (i + 1);
    		}
    		double depthVal = depthCharge(machine, roles, role, state, false, 0);
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
    */

}