package org.ggp.base.player.gamer.statemachine.propMCTS;

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
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public class propMCTS extends StateMachineGamer
{
	/* Node Structure for state tree
	 */
	public class Node {
		private List<Node> children = null;
		private double score;
		private MachineState state;
		private int visits;
		private Node parent;
		private List<Move> moves;

		public Node(MachineState state, double score, Node parent, List<Move> moves) {
			this.children = new ArrayList<>();
			this.score = score;
			this.state = state;
			this.visits = 0;
			this.parent = parent;
			this.moves = moves;
		}
	}

	static long DEPTH_TIME = 3000;
	static int LEVEL = 2;
	static Random r = new Random();
	static long MIN_SEARCH_TIME = 3000;

	private long searchTime = 3000;
	private int subgameIndex = -1;
	private double expansionFactor;
	private int expansionFactorTotal = 0;
	private int expansionFactorNum = 0;
	private double explorationTime;
	private int numCharges;

	private int chargesSent;

	private Node curNode;

	private long endtime;

	boolean first = true;

	boolean heurustic = false;
	float max_moves;
	float opp_max_moves;
	List<Double> weights = Arrays.asList(0.333, 0.333, 0.333);
	List<Double> currWeights = weights;
	int maxMetaReward = 0;

	static int L = 2;
	int level = 0;
	int hnum = 0;

	@Override
	public String getName() {
		return "SewardsFollyPropnetMCTS";
	}

	@Override
    public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new SamplePropNetStateMachine());
    }

	@Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

	@Override
    public void stateMachineAbort() {
        // Propnet MCTS gamer has no special cleanup when the match ends abruptly.
    }

    @Override
    public void stateMachineStop() {
        // Propnet MCTS gamer has no special cleanup when the match ends normally.
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException {
        // Propnet MCTS gamer has no game previewing.
    }

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		System.out.println("metagaming");
		endtime = timeout;
		long depth_start = System.currentTimeMillis();
		StateMachine machine = getStateMachine();
		machine.setRole(getRole());
		List<Role> roles = machine.getRoles();

		int numSubgames = machine.getNumSubgames();

		if (roles.size() == 1 && numSubgames > 1) {

			long subgameMetaTime = (timeout - System.currentTimeMillis()) / 2;
			long subgameMetaTimePerGame = subgameMetaTime / numSubgames;
			System.out.println("subgameMetaTimePerGame: " + subgameMetaTimePerGame);

			List<Integer> chargeList = new ArrayList<Integer>();
			List<Double> scoreList = new ArrayList<Double>();
			List<Long> expList = new ArrayList<Long>();
			for (int i = 0; i < numSubgames; i++) {
				System.out.println("game: " + i + " of " + numSubgames);
				chargeList.add(0);
				scoreList.add(0.0);
				long subgameEnd = System.currentTimeMillis() + subgameMetaTimePerGame;
				while(System.currentTimeMillis() < subgameEnd) {
					//System.out.println("test");
					double score = depthCharge(machine, machine.getRoles(), getRole(), machine.getInitialState(), true, i, 0);
					scoreList.set(i, scoreList.get(i) + score);
					chargeList.set(i, chargeList.get(i) + 1);
				}
				scoreList.set(i, scoreList.get(i) / chargeList.get(i));
				System.out.println("total number of charges: " + chargeList.get(i));
				System.out.println("total score: " + scoreList.get(i));
				long expTime = 1 + subgameMetaTimePerGame / chargeList.get(i);
				expList.add(expTime);
				System.out.println("observed time per depth charge: " + expTime);
				double expFactor = expansionFactorTotal / (double) expansionFactorNum;
				System.out.println("expansion factor: " + expFactor);
				expansionFactorTotal = 0;
				expansionFactorNum = 0;

				int nCharges = (int) (subgameMetaTimePerGame / (expTime * Math.pow(expFactor, LEVEL)));
				chargeList.set(i, nCharges);
				System.out.println("score: " + scoreList.get(i) + " | numCharges: " + chargeList.get(i));
			}
			numCharges = chargeList.get(0);
			subgameIndex = 0;
			for (int i = 0; i < numSubgames; i++) {
				if (scoreList.get(i) < scoreList.get(subgameIndex)) {
					numCharges = chargeList.get(i);
					subgameIndex = i;
				}
			}
			System.out.println("Num charges: " + numCharges + " | Subgame index: " + subgameIndex);
		} else {
			int charges = 0;
			while (System.currentTimeMillis() - depth_start < DEPTH_TIME) {
				//System.out.println("Starting a new depth charge");
				//System.out.println(machine.getInitialState());
				depthCharge(machine, machine.getRoles(), getRole(), machine.getInitialState(),
						true, -1, 0);
				charges++;
				// if(charges == 10) {int i = 1/0;}
			}
			explorationTime = (System.currentTimeMillis() - depth_start) / charges + 1;
			expansionFactor = expansionFactorTotal / (double) expansionFactorNum;
			System.out.println("time: " + explorationTime + " | e-factor: " + expansionFactor);
			System.out.println("set time: " + machine.getSetTime());
			numCharges = (int) ((timeout - System.currentTimeMillis()) / (explorationTime * Math.pow(expansionFactor, LEVEL)));
			if(numCharges == 0) {
				numCharges = 1;
			}
			System.out.println("num charges: " + numCharges);
			System.out.println("");
		}
		curNode = new Node(machine.getInitialState(), 0, null, null);
		int nodesExplored = 0;
		chargesSent = 0;
		depth_start = System.currentTimeMillis();
		while (timeout - System.currentTimeMillis() >= searchTime) {
			// System.out.println("New select/expand, time left: " + (timeout - System.currentTimeMillis() - SEARCH_TIME));
			Node selected = select(curNode);
			expand(selected, machine, getRole());
			nodesExplored++;
		}
		System.out.println("nodes explored: " + nodesExplored);
		System.out.println(chargesSent);
		System.out.println("charges/second: " + (chargesSent / ((System.currentTimeMillis() - depth_start) / 1000.0 )));

		first = true;
	}

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		endtime = timeout;
    	chargesSent = 0;

    	System.out.println("NEW SELECT");
    	/*
    	if(hnum == 5) {
    		hnum = 0;
    		heurustic = false;
    		System.out.println("back to propnet");
    	}
    	*/
    	if(heurustic) {
    		return heuristicMove(timeout);
    	}


		numCharges = (int) ((timeout - System.currentTimeMillis()) / (explorationTime * Math.pow(expansionFactor, LEVEL)));
		if (numCharges == 0) {
			numCharges = 1;
		}

		System.out.println("num charges: " + numCharges);

    	long start = System.currentTimeMillis();
		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState(), subgameIndex);

		Node curState = null;
		//System.out.println("is first: " + first);
		if (first) {
			curState = curNode;
			first = false;
			System.out.println("first");
			System.out.println(curState);
		} else {
			MachineState state = getCurrentState();
			for (int i = 0; i < curNode.children.size(); i++) {
				if (curNode.children.get(i).state.equals(state)) {
					curState = curNode.children.get(i);
					curNode = curState;
					break;
				}
			}
		}

		//selects and expands on a node until time is up or all nodes have been searched
		long startLoop = System.currentTimeMillis();
		boolean firstLoop = true;
		System.out.println(searchTime);
		int numSelected = 0;
		while(timeout - System.currentTimeMillis() >= searchTime) {
			Node selected = select(curState);
			expand(selected, machine, getRole());
			if (firstLoop) {
				firstLoop = false;
				System.out.println(searchTime);
			}
			numSelected++;
		}
		if(numSelected < 5) {
			System.out.println("switching to H");
			heurustic = true;
		}

		System.out.println("charges sent: " + chargesSent);

		Role role = getRole();
		List<Role> roles = machine.getRoles();

		int roleIndex = 0;
		for(int i = 0; i < roles.size(); i++) {
			if(role.equals(roles.get(i))) {
				roleIndex = i;
				break;
			}
		}

		//selects the best move based on the scores of the child nodes
		Move bestMove = null;
		double bestScore = 0;
		for (int i = 0; i < curState.children.size(); i++) {
			System.out.println("move: " + curState.children.get(i).moves.get(roleIndex)+ " | raw score: " + curState.children.get(i).score + " | visits: " + curState.children.get(i).visits + " | true score " + selectfn(curState.children.get(i)));
			if (curState.children.get(i).score >= bestScore || bestMove == null) {
				bestMove = curState.children.get(i).moves.get(roleIndex);
				bestScore = curState.children.get(i).score;
			}
		}

		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, bestMove, stop - start));
        return bestMove;
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
    		//System.out.println(node.children.get(i).visits);
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

    	return result == null ? node : select(result);
    }

    double selectfn(Node node) {
    	return (double) (node.score + 50*Math.sqrt(Math.log(node.parent.visits)/node.visits));
    }

    /*
     * expands on a node and gets scores for the node and its new children
     */
    private void expand(Node node, StateMachine machine, Role role) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	//avoid expanding a null node
    	if (node == null) {
    		return;
    	}

		//if terminal then a MCS is not needed
    	if (machine.findTerminalp(node.state, subgameIndex, 0)) {
    		node.visits++;
    		int score = machine.findReward(role, node.state, subgameIndex, 0);
    		node.score = score;
    		backpropogate(node.parent, score);
    		return;
    	}

    	//run a MCS for each action
    	List<List<Move>> actions = getActions(machine.getRoles(), machine, node.state);
    	List<Move> action = actions.get(r.nextInt(actions.size()));

    	MachineState newstate = machine.getNextState(node.state, action);
    	double totalscore = 0;
    	for(int j = 0; j < numCharges; j++) {
    		if((endtime - System.currentTimeMillis() < searchTime)) break;
    		double score = (double) depthCharge(machine, machine.getRoles(), role, newstate, false, subgameIndex, 0);
    		totalscore += score;
    		chargesSent++;
    	}

    	totalscore /= numCharges;
   		//add nodes to tree
   		Node newNode = new Node(newstate, totalscore, node, action);
   		node.children.add(newNode);
   		backpropogate(newNode.parent, totalscore);

   		for(int i = 0; i < actions.size(); i++) {
   			if(!actions.get(i).equals(action)) {
   		    	newstate = machine.getNextState(node.state, actions.get(i));

   				newNode = new Node(newstate, 0, node, actions.get(i));
   				node.children.add(newNode);
   			}
   		}

    }

    private void backpropogate(Node node, double score) {
    	node.visits++;
    	node.score += (score - node.score)/node.visits;
    	//adjust to avoid always looking at the same nodes
    	if(node.parent != null) {
    		backpropogate(node.parent, score);
    	}
    }

    private double depthCharge(StateMachine machine, List<Role> roles, Role role, MachineState state, boolean meta, int gameIndex, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	System.out.println("start state: " + state);
    	while(!machine.findTerminalp(state, gameIndex, level)) {
    		if ((endtime - System.currentTimeMillis() < searchTime)) {
    			return 0;
    		}
    		List<Move> actions = new ArrayList<Move>();

    		for (int i = 0; i < roles.size(); i++) {
        		List<Move> legals = machine.findLegals(roles.get(i), state, gameIndex);
        		if (meta && roles.get(i).equals(role)) {
        			expansionFactorTotal += legals.size();
        			expansionFactorNum++;
        		}
        		if (legals.size() > 0) {
        			actions.add(legals.get(r.nextInt(legals.size())));
        		} else {
        			actions.add(null);
        		}
        	}
        	state = machine.getNextState(state, actions);
        	System.out.println("new state: " + state);
    	}
    	System.out.println("ended");
    	return machine.findReward(role, state, gameIndex, level);
    }

    // get a list of all possible permutations of actions
    // by all players (possible more than 2)
    private List<List<Move> > getActions(List<Role> roles,
    		StateMachine machine, MachineState state) throws MoveDefinitionException {
    	List<List<Move> > actions = new ArrayList<List<Move> >();
    	List<Move> curr = new ArrayList<Move>();
    	recursiveActionHelper(roles, machine, state, 0, curr, actions);
    	return actions;
    }

    // recursive action possibility builder
    private void recursiveActionHelper(List<Role> roles,
    		StateMachine machine, MachineState state, int i,
    		List<Move> curr, List<List<Move> > actions) throws MoveDefinitionException {
    	if (i == roles.size()) {
    		actions.add(curr);
    		return;
    	} else {
    		List<Move> possibleActions;
   			possibleActions = machine.getLegalMoves(state, roles.get(i), subgameIndex);

    		for (int j = 0; j < possibleActions.size(); j++) {
    			List<Move> dest = new ArrayList<Move>(curr);
    			dest.add(possibleActions.get(j));
    			recursiveActionHelper(roles, machine, state, i + 1, dest, actions);
    		}
    	}
    }


    public Move heuristicMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
    	hnum++;
    	max_moves = 0;
    	opp_max_moves = 0;

    	long start = System.currentTimeMillis();

		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState(), subgameIndex);

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
    	List<Move> actions = machine.findLegals(role, state, subgameIndex);
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
    		if(timeout - System.currentTimeMillis() < searchTime) break;
    		System.out.println("looked at action " + i);
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
    	level++;
    	List<List<Move>> actions = getActions(roles, machine, state);
    	if(numOppMoves(machine, roles, role, state) > opp_max_moves) {
    		opp_max_moves = numOppMoves(machine, roles, role, state);
    	}
    	if(actions.size() > max_moves) {
    		max_moves = actions.size();
    	}

    	for (int i = 0; i < actions.size(); i++) {
    		if(timeout - System.currentTimeMillis() < searchTime) break;
    		MachineState newState = machine.findNext(actions.get(i), state);
    		float result = maxScore(machine, roles, role, newState, alpha, beta, timeout);
    		beta = Math.min(beta, result);
    		if (beta <= alpha) {
    			return alpha;
    		}
    	}
    	level--;
    	return beta;
    }

    private float maxScore(StateMachine machine, List<Role> roles, Role role, MachineState state, float alpha, float beta, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	// System.out.println("alpha: " + alpha + " | beta: " + beta);
    	if (machine.findTerminalp(state, subgameIndex, 0)) {
    		return machine.findReward(role, state, subgameIndex, 0);
    	}

    	if(timeout - System.currentTimeMillis() < searchTime || level > L) {
    		List<Float> values = new ArrayList<Float>();

    		values.add(machine.findLegals(role, state, subgameIndex).size()/max_moves);
    		values.add(1 - (numOppMoves(machine, roles, role, state)/ opp_max_moves));
    		values.add((float)machine.findReward(role, state, subgameIndex, 0));
    		float score = 0;
    		for(int i = 0; i < currWeights.size(); i++) {
    			score += currWeights.get(i) * values.get(i);
    		}
    		return score;

    	}

    	List<Move> actions = machine.findLegals(role, state, subgameIndex);
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
				opp_moves += machine.findLegals(roles.get(i), state, subgameIndex).size();
			}
		}
	    return opp_moves;
    }
}