# Brief Illustration for Core Modules in UUTestBot

## `Monkey.java`

* **Line 722**: Function *run()*
	* Run the testing command!

	* **Lines 790-796:** Collect all the activities of the target App to calculate the `Activity Coverage` upon the end of the test.

	* **Lines 824-853:** Initialize the Android device and setup the `EventSource` with our testing strategy.

	* **Line 898:** Function *runMonkeyCycles()* (See line 1416 in `Monkey.java`).

	* **Lines 906-910:** Tear down the `EventSource` and calculate the `Activity Coverage`.

* **Line 1416:** Function *runMonkeyCycles()*
	* Run cycles for mCount times or within a given time limit, and see if we hit any crashers.

	* **Line 1543:** Function *getNextEvent()* (See line 1359 in `MonkeySourceApe.java`).

	* **Line 1545:** If the event is not null, attempt to inject it.

## `MonkeySourceApe.java`

* **Line 216:** Constructor *MonkeySourceApe(args)*
	* **Lines 244-256:** Setup the `UtbAgent` (See line 100/150 in `UtbAgent.java`).

* **Line 1359:** Function *getNextEvent()*
	* If the event queue is empty, we generate events first. If it is not empty, we pop a event from it.

	* **Line 1363:** Function *generateEvents()* (See line 837 in `MonkeySourceApe.java`).

* **Line 837:** Function *generateEvents()*
	* Generate an event based on the strategy in `UtbAgent.java`.

	* **Line 860:** Function *updateState(args)* (See line 1012 in `UtbAgent.java`).

## `UtbAgent.java`

* **Line 100:** Constructor *UtbAgent(MonekySourceApe ape)*
* **Line 150:** Constructor *UtbAgent(MonekySourceApe ape, File utbStateFile)*
	* Setup the `UtbAgent` instance (If a utbStateFile is detected in `/sdcard`, the 2nd constructor is used).

* **Line 1012:** Function *updateState(args)*
	* Jump to function *updateStateWrapper(args)* (See line 1018 in `UtbAgent.java`).

* **Line 1018:** Function *updateStateWrapper(args)*
	* Jump to function *updateStateInternal(args)* (See line 740 in `UtbAgent.java`).

* **Line 740:** Function *updateStateInternal(args)*
	* Core logic for the automatic testing strategy, utilizing our enhanced approach originated from Fastbot2.

	* **Line 742:** Function *buildGUITree(args)*
		* Dump the GUITree from the current GUI on the connected android device.

	* **Line 745:** Function *buildState(args)*
		* Setup the `UtbState` instance (See line 25 in `UtbState.java`).

	* **Line 750:** Function *getHyperEvents()*
		* Generate the list of *HyperEvents* (Defined in Fastbot2) (See line 33 in `UtbState.java`).

	* **Lines 860-866:** The probabilistic model expansion phase in Fastbot2.

	* **Lines 867-871:** The Q-learning (N-step SARSA) phase in Fastbot2.

	* **Lines 872-905:** The probabilistic model exploitation phase in Fastbot2.

	* **Lines 913-943:** Action selecting strategy (Random picking & weight-based scroll picking).

	* **Lines 945-957:** States recording and value updating.

## `UtbState.java`

* **Line 25:** Constructor *UtbState(args)*

* **Line 33:** Function *generateHyperEvents()*
	* Generate the list of *HyperEvents* (Defined in Fastbot2) through reconstructing the `GUITreeNode` data.