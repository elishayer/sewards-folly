package org.ggp.base.player.gamer.statemachine.propMCTS;

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
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public final class propMCTS extends StateMachineGamer
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

	static long DEPTH_TIME = 1500;
	static long SEARCH_TIME = 1500;
	static int LEVEL = 2;
	static Random r = new Random();

	private double expansionFactor;
	private int expansionFactorTotal = 0;
	private int expansionFactorNum = 0;
	private double explorationTime;
	private int numCharges;

	private int chargesSent;

	private int unexplored;

	private Node curNode;

	boolean first = true;

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
		System.out.println("Meta gaming starts");
		StateMachine machine = getStateMachine();

		long start = System.currentTimeMillis();

		int charges = 0;
		long depth_start = System.currentTimeMillis();
		while(System.currentTimeMillis() - start < DEPTH_TIME) {
			System.out.println("Starting a new depth charge");
			depthCharge(machine, machine.getRoles(), getRole(), machine.getInitialState(),
					true, 0);
			charges++;
		}
		explorationTime = (System.currentTimeMillis() - depth_start) / charges + 1;
		expansionFactor = expansionFactorTotal / (double) expansionFactorNum;
		System.out.println("time: " + explorationTime + " | e-factor: " + expansionFactor);
		numCharges = (int) ((timeout - System.currentTimeMillis()) / (explorationTime * Math.pow(expansionFactor, LEVEL)));
		System.out.println("charges: " + numCharges);

		curNode = new Node(machine.getInitialState(), 0, null, null);

		while(timeout - System.currentTimeMillis() >= SEARCH_TIME) {
			Node selected = select(curNode);
			expand(selected, machine, getRole());
		}

		first = true;
		return;
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		chargesSent = 0;

    	System.out.println("NEW SELECT");

		numCharges = (int) ((timeout - System.currentTimeMillis()) / (explorationTime * Math.pow(expansionFactor, LEVEL)));
		System.out.println("charges: " + numCharges);

    	long start = System.currentTimeMillis();
		StateMachine machine = getStateMachine();

		List<Move> moves = machine.findLegals(getRole(), getCurrentState());

		Node curState = null;
		System.out.println("is first: " + first);
		if(first) {
			curState = curNode;
			first = false;
			System.out.println("first");
		} else {
			MachineState state = getCurrentState();
			for(int i = 0; i < curNode.children.size(); i++) {
				if(curNode.children.get(i).state.equals(state)) {
					curState = curNode.children.get(i);
					curNode = curState;
					System.out.println("chosen");
					break;
				}
			}
		}

		if(curState != null) {
		System.out.println(curState.score + " | " + curState.visits);
		}
		//tracks number of unexplored nodes in the treee
		unexplored = 1;


		//selects and expands on a node until time is up or all nodes have been searched
		while(timeout - System.currentTimeMillis() >= SEARCH_TIME && unexplored > 0) {
			Node selected = select(curState);
			expand(selected, machine, getRole());
			//System.out.println("undexplored: " + unexplored);
			//System.out.println("");
		}

		System.out.println("charges sent: " + chargesSent);

		//System.out.println(getNodeCount(curState, machine));
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
			if (curState.children.get(i).score >= bestScore) {
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
    	//System.out.println("select");
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

    	//if(node.children.size() == 0) return null;

    	//return select(node.children.get(r.nextInt(node.children.size())));
    	return select(result);
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

		unexplored--;

		//if terminal then a MCS is not needed
    	if (machine.findTerminalp(node.state)) {
    		node.visits++;
    		int score = machine.findReward(role, node.state);
    		node.score = score;
    		backpropogate(node.parent, score);
    		return;
    	}

    	//run a MCS for each action
    	List<List<Move>> actions = getActions(machine.getRoles(), machine, node.state);
    	List<Move> action = actions.get(r.nextInt(actions.size()));

    	MachineState newstate = machine.getNextState(node.state, action);
    	double totalscore = 0;
    	for(int j = 0; j < numCharges; j++) { //place holder #
    		chargesSent++;
    		double score = (double) depthCharge(machine, machine.getRoles(), role, newstate, false, 0);
    		totalscore += score;

    	}
    	totalscore /= numCharges;
   		//add nodes to tree
   		Node newNode = new Node(newstate, totalscore, node, action);
   		node.children.add(newNode);
   		unexplored++;
   		backpropogate(newNode.parent, totalscore);

   		for(int i = 0; i < actions.size(); i++) {
   			if(!actions.get(i).equals(action)) {
   		    	newstate = machine.getNextState(node.state, actions.get(i));

   				newNode = new Node(newstate, 0, node, actions.get(i));
   				node.children.add(newNode);
   		   		unexplored++;

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

    private double depthCharge(StateMachine machine, List<Role> roles, Role role, MachineState state, boolean meta, int level) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
    	System.out.println("depth charge starts here " + state);
    	while(!machine.findTerminalp(state)) {
    		// System.out.println("one level deeper!");
    		List<Move> actions = new ArrayList<Move>();
        	for (int i = 0; i < roles.size(); i++) {
        		List<Move> legals = machine.findLegals(roles.get(i), state);
        		// System.out.println("legals: " + legals);
        		if (meta && roles.get(i).equals(role)) {
        			expansionFactorTotal += legals.size();
        			expansionFactorNum++;
        		}
        		actions.add(legals.get(r.nextInt(legals.size())));
        	}
        	// System.out.println(actions);
        	// System.out.println("current state: " + state + ", next state: " + machine.getNextState(state, actions));
        	state = machine.getNextState(state, actions);
    	}

    	return machine.findReward(role, state);
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
    		List<Move> possibleActions = machine.getLegalMoves(state, roles.get(i));
    		for (int j = 0; j < possibleActions.size(); j++) {
    			List<Move> dest = new ArrayList<Move>(curr);
    			dest.add(possibleActions.get(j));
    			recursiveActionHelper(roles, machine, state, i + 1, dest, actions);
    		}
    	}
    }
}