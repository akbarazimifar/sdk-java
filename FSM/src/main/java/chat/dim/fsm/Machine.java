/* license: https://mit-license.org
 *
 *  Finite State Machine
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim.fsm;

import java.lang.ref.WeakReference;

public abstract class Machine<S extends State> {

    private enum Status {
        Stopped (0),
        Running (1),
        Paused  (2);

        final int value;

        Status(int value) {
            this.value = value;
        }
    }
    private Status status = Status.Stopped;

    private WeakReference<StateDelegate<S>> delegateRef = null;

    private S currentState = null;
    private String defaultStateName;

    protected Machine(String defaultStateName) {
        super();
        this.defaultStateName = defaultStateName;
    }

    public void setDelegate(StateDelegate<S> delegate) {
        if (delegate == null) {
            delegateRef = null;
        } else {
            delegateRef = new WeakReference<>(delegate);
        }
    }
    public StateDelegate<S> getDelegate() {
        if (delegateRef == null) {
            return null;
        } else {
            return delegateRef.get();
        }
    }

    public S getCurrentState() {
        return currentState;
    }

    /**
     *  add state with name
     *
     * @param name - name for state
     * @param state - finite state
     */
    public abstract void addState(String name, S state);

    protected abstract S getState(String name);

    public void changeState(String stateName) {
        StateDelegate<S> delegate = getDelegate();

        // exit current state
        if (currentState != null) {
            if (delegate != null) {
                delegate.exitState(currentState, this);
            }
            currentState.onExit(this);
        }

        S newState = getState(stateName);
        currentState = newState;

        // enter new state
        if (newState != null) {
            if (delegate != null) {
                delegate.enterState(newState, this);
            }
            newState.onEnter(this);
        }
    }

    /**
     *  start machine from default state
     */
    public void start() {
        assert currentState == null && Status.Stopped.equals(status) : "FSM start error: " + status + ", " + currentState;
        changeState(defaultStateName);
        status = Status.Running;
    }

    /**
     *  stop machine and set current state to null
     */
    public void stop() {
        assert currentState != null && !Status.Stopped.equals(status) : "FSM stop error: " + status + ", " + currentState;
        status = Status.Stopped;
        changeState(null);
    }

    /**
     *  pause machine, current state not change
     */
    public void pause() {
        assert currentState != null && Status.Running.equals(status) : "FSM pause error: " + status + ", " + currentState;
        StateDelegate<S> delegate = getDelegate();
        if (delegate != null) {
            delegate.pauseState(currentState, this);
        }
        status = Status.Paused;
        currentState.onPause(this);
    }

    /**
     *  resume machine with current state
     */
    public void resume() {
        assert currentState != null && Status.Paused.equals(status) : "FSM resume error: " + status + ", " + currentState;
        StateDelegate<S> delegate = getDelegate();
        if (delegate != null) {
            delegate.resumeState(currentState, this);
        }
        status = Status.Running;
        currentState.onResume(this);
    }

    /**
     *  Drive the machine running forward
     */
    public void tick() {
        if (status == Status.Running) {
            currentState.tick(this);
        }
    }
}
