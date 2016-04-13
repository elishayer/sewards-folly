package org.ggp.base.player.gamer.statemachine.singleplayer;

import java.util.Arrays;
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

public final class SinglePlayerGamer extends StateMachineGamer
{
	int step;
	List<Move> plan;

	private class result {
		int score;
		List<Move> subplan;
	}

	@Override
	public String getName() {
		return "SewardsFollySingle";
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
		step = 0;
		StateMachine machine = getStateMachine();
		MachineState state = machine.getInitialState();
		Role role = getRole();
		plan = bestplan(role, machine, state).subplan;
    }

	private result bestplan(Role role, StateMachine machine, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if(machine.isTerminal(state)) {
			result r = new result();
			r.score = machine.findReward(role, state);
			return r;
		}
		List<Move> moves = machine.findLegals(role, state);
		System.out.println(moves);
		result r = bestplan(role, machine, machine.findNext(Arrays.asList(moves.get(0)), state));
		int score = r.score;
		List<Move> plan = r.subplan;
		plan.set(plan.size(), moves.get(0));
		for(int i = 0; i < moves.size(); i++) {
			r = bestplan(role, machine, machine.findNext(Arrays.asList(moves.get(i)), state));
			if(r.score > score) {
				score = r.score;
				plan = r.subplan;
				plan.set(plan.size(), moves.get(i));
			}
		}
		return r;
	}


    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		long start = System.currentTimeMillis();
		System.out.println(plan);

		Move move = plan.get(step);
		step++;

		StateMachine machine = getStateMachine();
		List<Move> moves = machine.findLegals(getRole(), getCurrentState());
		long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, move, stop - start));
        return move;
    }

}