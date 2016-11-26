/**
 * AIWolfGame.java
 * 
 * Copyright (c) 2016 人狼知能プロジェクト
 */
package org.aiwolf.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Guard;
import org.aiwolf.common.data.Judge;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.data.Species;
import org.aiwolf.common.data.Status;
import org.aiwolf.common.data.Talk;
import org.aiwolf.common.data.Team;
import org.aiwolf.common.data.Vote;
import org.aiwolf.common.net.GameSetting;
import org.aiwolf.common.util.Counter;
import org.aiwolf.server.net.GameServer;
import org.aiwolf.server.util.FileGameLogger;
import org.aiwolf.server.util.GameLogger;

/**
 * Game Class of AI Wolf Contest
 * @author tori
 *
 */
public class AIWolfGame {


	protected Random rand;

	/**
	 * Settings of the game
	 */
	protected GameSetting gameSetting;

	/**
	 * server to connect clients
	 */
	protected GameServer gameServer;

	/**
	 *
	 */
	protected Map<Integer, GameData> gameDataMap;

	/**
	 *
	 */
	protected GameData gameData;

	/**
	 * Show console log?
	 */
	protected boolean isShowConsoleLog = true;


	/**
	 * ログを記録するファイル
	 */
//	protected File logFile;

	/**
	 * Logger
	 */
	protected GameLogger gameLogger;

	/**
	 * Name of Agents
	 */
	protected Map<Agent, String> agentNameMap;

	/**
	 *
	 */
	public AIWolfGame(GameSetting gameSetting, GameServer gameServer) {
		rand = new Random();
		this.gameSetting = gameSetting;
		this.gameServer = gameServer;

//		gameLogger = AiWolfLoggerFactory.getSimpleLogger(this.getClass().getSimpleName());
	}


	/**
	 * @return logFile
	 */
//	public File getLogFile() {
//		return logFile;
//	}


	/**
	 * @param logFile セットする logFile
	 * @throws IOException
	 */
	public void setLogFile(File logFile) throws IOException {
//		this.logFile = logFile;
		gameLogger = new FileGameLogger(logFile);
	}

	/**
	 * set GameLogger
	 * @param gameLogger
	 */
	public void setGameLogger(GameLogger gameLogger){
		this.gameLogger = gameLogger;
	}

	/**
	 * get GameLogger
	 */
	public GameLogger getGameLogger(){
		return this.gameLogger;
	}

	
	/**
	 * Set Random Class
	 * @param rand
	 */
	public void setRand(Random rand) {
		this.rand = rand;
	}

	/**
	 * Initialize Game
	 */
	protected void init(){
		gameDataMap = new TreeMap<Integer, GameData>();
		gameData = new GameData(gameSetting);
		agentNameMap = new HashMap<Agent, String>();
		gameServer.setGameData(gameData);

		List<Agent> agentList = gameServer.getConnectedAgentList();

		if(agentList.size() != gameSetting.getPlayerNum()){
			throw new IllegalPlayerNumException("Player num is "+gameSetting.getPlayerNum()+" but connected agent is "+agentList.size());
		}

		Collections.shuffle(agentList, rand);

		Map<Role, List<Agent>> requestRoleMap = new HashMap<Role, List<Agent>>();
		for(Role role:Role.values()){
			requestRoleMap.put(role, new ArrayList<Agent>());
		}
		List<Agent> noRequestAgentList = new ArrayList<Agent>();
		for(Agent agent:agentList){
			Role requestedRole = gameServer.requestRequestRole(agent);
			if(requestedRole != null){
				if(requestRoleMap.get(requestedRole).size() < gameSetting.getRoleNum(requestedRole)){
					requestRoleMap.get(requestedRole).add(agent);
				}
				else{
					noRequestAgentList.add(agent);
				}
//				System.out.println(agent+" request "+requestedRole);
			}
			else{
				noRequestAgentList.add(agent);
//				System.out.println(agent+" request no role");
			}
		}


		for(Role role:Role.values()){
			List<Agent> requestedAgentList = requestRoleMap.get(role);
			for(int i = 0; i < gameSetting.getRoleNum(role); i++){
				if(requestedAgentList.isEmpty()){
					gameData.addAgent(noRequestAgentList.remove(0), Status.ALIVE, role);
				}
				else{
					gameData.addAgent(requestedAgentList.remove(0), Status.ALIVE, role);
				}
			}
		}
		
		gameDataMap.put(gameData.getDay(), gameData);

		gameServer.setGameData(gameData);
		gameServer.setGameSetting(gameSetting);
		for(Agent agent:agentList){
			gameServer.init(agent);
			String requestName = gameServer.requestName(agent);
			agentNameMap.put(agent, requestName);
//			System.out.println(requestName);
		}
	}


	/**
	 * Start game
	 */
	public void start(){
		try{
			init();
		
		//		System.out.printf("%d-%d\n", getAliveHumanList().size(), getAliveWolfList().size());
			while(!isGameFinished()){
				consoleLog();
		
				day();
				night();
				if(gameLogger != null){
					gameLogger.flush();
				}
			}
			consoleLog();
			finish();
		
			if(isShowConsoleLog){
				System.out.println("Winner:"+getWinner());
			}
		//		for(Agent agent:gameData.getAgentList()){
		//			GameInfo gameInfo = gameData.getGameInfo(agent);
		////			System.out.println(JSON.encode(gameInfo));
		//			break;
		//		}
		}catch(LostClientException e){
			if(gameLogger != null){
				gameLogger.log("Lost Connection of "+e.getAgent());
			}
			throw e;
		}
	}

	public void finish(){
		if(gameLogger != null){
			for(Agent agent:new TreeSet<Agent>(gameData.getAgentList())){
				gameLogger.log(String.format("%d,status,%d,%s,%s,%s", gameData.getDay(), agent.getAgentIdx(),gameData.getRole(agent), gameData.getStatus(agent), agentNameMap.get(agent)));
			}
			gameLogger.log(String.format("%d,result,%d,%d,%s", gameData.getDay(),  getAliveHumanList().size(), getAliveWolfList().size(), getWinner()));
			gameLogger.close();
		}

		for(Agent agent:gameData.getAgentList()){
//			System.out.println("Send finish to "+agent);
			gameServer.finish(agent);
		}
/*		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}*/
	}

	/**
	 * Get won team.
	 * if game not finished, return null
	 * @return
	 */
	public Team getWinner(){
		int humanSide = 0;
		int wolfSide = 0;
		int otherSide = 0;
		for(Agent agent:gameData.getAgentList()){
			if(gameData.getStatus(agent) == Status.DEAD){
				continue;
			}
			
			
			if(gameData.getRole(agent).getTeam() == Team.OTHERS){
				otherSide++;
			}
			if(gameData.getRole(agent).getSpecies() == Species.HUMAN){
				humanSide++;
			}
			else{
				wolfSide++;
			}
		}
		if(wolfSide == 0){
			if(otherSide > 0){
				return Team.OTHERS;
			}
			return Team.VILLAGER;
		}
		else if(humanSide <= wolfSide){
			if(otherSide > 0){
				return Team.OTHERS;
			}
			return Team.WEREWOLF;
		}
		else{
			return null;
		}

	}

	private void consoleLog() {

		if(!isShowConsoleLog){
			return;
		}

		GameData yesterday = gameData.getDayBefore();

		System.out.println("=============================================");
		if(yesterday != null){
			System.out.printf("Day %02d\n", yesterday.getDay());
			System.out.println("========talk========");
			for(Talk talk:yesterday.getTalkList()){
				System.out.println(talk);
			}
			System.out.println("========Whisper========");
			for(Talk whisper:yesterday.getWhisperList()){
				System.out.println(whisper);
			}

			System.out.println("========Actions========");
			for(Vote vote:yesterday.getVoteList()){
				System.out.printf("Vote:%s->%s\n", vote.getAgent(), vote.getTarget());
			}

//			System.out.println("Attack Vote Result");
			for(Vote vote:yesterday.getAttackVoteList()){
				System.out.printf("AttackVote:%s->%s\n", vote.getAgent(), vote.getTarget());
			}

			Judge divine = yesterday.getDivine();
			System.out.printf("%s banished\n", yesterday.getBanished());
			if(divine != null){
				System.out.printf("%s divine %s. Result is %s\n", divine.getAgent(), divine.getTarget(), divine.getResult());
			}
			Guard guard = yesterday.getGuard();
			if(guard != null){
				System.out.printf("%s guarded\n", guard);
			}

			// TODO 妖狐導入の際は要変更
			if (!yesterday.getLastDeadAgentList().isEmpty()) {
				System.out.printf("%s attacked\n", yesterday.getLastDeadAgentList().get(0));
			}

		}
		System.out.println("======");
		List<Agent> agentList = gameData.getAgentList();
		Collections.sort(agentList, new Comparator<Agent>() {
			@Override
			public int compare(Agent o1, Agent o2) {
				return o1.getAgentIdx()-o2.getAgentIdx();
			}
		});
		for(Agent agent:agentList){
			System.out.printf("%s\t%s\t%s\t%s", agent, agentNameMap.get(agent), gameData.getStatus(agent), gameData.getRole(agent));
			if(yesterday != null){
				if(yesterday.getBanished() == agent){
					System.out.print("\tbanished");
				}

				// TODO 妖狐導入の際は要変更
				if (yesterday.getLastDeadAgentList().contains(agent)) {
					System.out.print("\tattacked");
				}

				Judge divine = yesterday.getDivine();
				if(divine != null && divine.getTarget() == agent){
					System.out.print("\tdivined");
				}
				Guard guard = yesterday.getGuard();
				if(guard != null && guard.getTarget() == agent){
					System.out.print("\tguarded");
				}
			}
			System.out.println();
		}
		System.out.printf("Human:%d\nWerewolf:%d\n", getAliveHumanList().size(), getAliveWolfList().size());

		System.out.println("=============================================");
	}


	protected void day() {
		dayStart();
		if (gameData.getDay() == 0) {
			if (gameSetting.isTalkOnFirstDay()) {
				whisper();
				talk();
			}
		}
		else {
			talk();
		}
	}

	/**
	 *
	 */
	protected void night() {

		for(Agent agent:gameData.getAgentList()){
			gameServer.dayFinish(agent);
		}
		
		if(!gameSetting.isTalkOnFirstDay() && gameData.getDay() == 0){
			whisper();
		}

		// Vote and banish except day 0
		Agent banished = null;
		if (gameData.getDay() != 0) {
			for (int i = 0; i <= gameSetting.getMaxRevote(); i++) {
				vote();
				List<Agent> candidates = getVotedCandidates(gameData.getVoteList());
				if (candidates.size() == 1) {
					banished = candidates.get(0);
					break;
				}
			}

			if (banished == null && !gameSetting.isEnableNoBanish()) {
				List<Agent> candidates = getAliveAgentList();
				Collections.shuffle(candidates, rand);
				banished = candidates.get(0);
			}

			if (banished != null) {
				gameData.setBanishedTarget(banished);
				if (gameLogger != null) {
					gameLogger.log(String.format("%d,banish,%d,%s", gameData.getDay(), banished.getAgentIdx(), gameData.getRole(banished)));
				}
			}
		}

		// every day
		divine();

		if (gameData.getDay() != 0) {
			whisper();
			guard();

			// attackVote and attack except day 0
			Agent attacked = null;
			if (!(getAliveWolfList().size() == 1 && gameData.getRole(gameData.getBanished()) == Role.WEREWOLF)) {
				for (int i = 0; i <= gameSetting.getMaxAttackRevote(); i++) {
					attack();
					List<Vote> attackCandidateList = gameData.getAttackVoteList();
					Iterator<Vote> it = attackCandidateList.iterator();
					while (it.hasNext()) {
						Vote vote = it.next();
						if (vote.getAgent() == banished) {
							it.remove();
						}
					}
					List<Agent> candidates = getAttackVotedCandidates(attackCandidateList);
					if (candidates.size() == 1) {
						attacked = candidates.get(0);
						break;
					}
					if (gameSetting.isWhisperBeforeRevote()) {
						whisper();
					}
				}

				if (attacked == null && !gameSetting.isEnableNoAttack()) {
					List<Agent> candidates = getAliveHumanList();
					Collections.shuffle(candidates, rand);
					attacked = candidates.get(0);
				}

				gameData.setAttackedTarget(attacked);

				boolean isGuarded = false;
				if (gameData.getGuard() != null) {
					if (gameData.getGuard().getTarget().equals(attacked) && attacked != null) {
						if (gameData.getBanished() == null
								|| !gameData.getBanished().equals(gameData.getGuard().getAgent())) {
							isGuarded = true;
						}
					}
				}
				if (!isGuarded && attacked != null && gameData.getRole(attacked) != Role.FOX) {
					gameData.addLastDeadAgent(attacked);

					if (gameLogger != null) {
						gameLogger.log(String.format("%d,attack,%d,true", gameData.getDay(), attacked.getAgentIdx()));
					}
				} else if (attacked != null) {
					if (gameLogger != null) {
						gameLogger.log(String.format("%d,attack,%d,false", gameData.getDay(), attacked.getAgentIdx()));
					}
				} else {
					if (gameLogger != null) {
						gameLogger.log(String.format("%d,attack,-1,false", gameData.getDay()));
					}
				}
			}
		}

		gameData = gameData.nextDay();
		gameDataMap.put(gameData.getDay(), gameData);
		gameServer.setGameData(gameData);

	}


	/**
	 *
	 * @param voteList
	 * @return
	 */
	protected List<Agent> getVotedCandidates(List<Vote> voteList) {
		Counter<Agent> counter = new Counter<Agent>();
		for(Vote vote:voteList){
			if(gameData.getStatus(vote.getTarget()) == Status.ALIVE){
				counter.add(vote.getTarget());
			}
		}

		int max = counter.get(counter.getLargest());
		List<Agent> candidateList = new ArrayList<Agent>();
		for(Agent agent:counter){
			if(counter.get(agent) == max){
				candidateList.add(agent);
			}
		}
		return candidateList;
	}

	/**
	 *
	 * @param voteList
	 * @return
	 */
	protected List<Agent> getAttackVotedCandidates(List<Vote> voteList) {
		Counter<Agent> counter = new Counter<Agent>();
		for (Vote vote : voteList) {
			if (gameData.getStatus(vote.getTarget()) == Status.ALIVE
					&& gameData.getRole(vote.getTarget()) != Role.WEREWOLF) {
				counter.add(vote.getTarget());
			}
		}
		if (!gameSetting.isEnableNoAttack()) {
			for (Agent agent : getAliveHumanList()) {
				counter.add(agent);
			}
		}

		int max = counter.get(counter.getLargest());
		List<Agent> candidateList = new ArrayList<Agent>();
		for (Agent agent : counter) {
			if (counter.get(agent) == max) {
				candidateList.add(agent);
			}
		}
		return candidateList;
	}

	/**
	 *
	 */
	protected void dayStart(){
		if(gameLogger != null){
			for(Agent agent:new TreeSet<Agent>(gameData.getAgentList())){
				gameLogger.log(String.format("%d,status,%d,%s,%s,%s", gameData.getDay(), agent.getAgentIdx(),gameData.getRole(agent), gameData.getStatus(agent), agentNameMap.get(agent)));
			}
		}

		for(Agent agent:getGameData().getAgentList()){
			gameServer.dayStart(agent);
		}

	}

	/**
	 * First, all agents have chances to talk.
	 * Next, wolves whispers.
	 * Continue them until all agents finish talking
	 */
	protected void talk() {

		List<Agent> aliveList = getAliveAgentList();
		for(Agent agent:aliveList){
			gameData.remainTalkMap.put(agent, gameSetting.getMaxTalk());
		}

		Counter<Agent> skipCounter = new Counter<>();
		for(int time = 0; time < gameSetting.getMaxTalkTurn(); time++){
			Collections.shuffle(aliveList);

			List<Talk> talkList = new ArrayList<>();
			for(Agent agent:aliveList){
				String talkText = Talk.OVER;
				if(gameData.getRemainTalkMap().get(agent) > 0){
					talkText = gameServer.requestTalk(agent);
				}
//				String talkContent = gameServer.requestTalk(agent);
				if(talkText == null || talkText.isEmpty()){
					talkText = Talk.SKIP;
				}
				if(talkText == Talk.SKIP){
					skipCounter.add(agent);
					if(skipCounter.get(agent) > gameSetting.getMaxSkip()){
						talkText = Talk.OVER;
					}
				}
				Talk talk = new Talk(gameData.nextTalkIdx(), gameData.getDay(), time, agent, talkText);
				talkList.add(talk);

				if(!talk.isOver() && !talk.isSkip()){
					skipCounter.put(agent, 0);
				}
			}

//			Collections.shuffle(talkList);
			boolean continueTalk = false;
			for(Talk talk:talkList){
				gameData.addTalk(talk.getAgent(), talk);
				if(gameLogger != null){
					//TODO Talkの仕様変更に従って，ログを変更する
					gameLogger.log(String.format("%d,talk,%d,%d,%d,%s", gameData.getDay(), talk.getIdx(), talk.getTurn(), talk.getAgent().getAgentIdx(), talk.getText()));
				}
				if(!talk.isOver()){
					continueTalk = true;
				}
			}
			
			if(!continueTalk){
				break;
			}

		}
	}

	protected void whisper() {
		//Whisper by werewolf
		List<Agent> aliveWolfList = gameData.getFilteredAgentList(getAliveAgentList(), Role.WEREWOLF);
		if(aliveWolfList.size() == 1){
//			return;
		}
		for(Agent agent:aliveWolfList){
			gameData.remainWhisperMap.put(agent, gameSetting.getMaxWhisper());
		}

		
		Counter<Agent> skipCounter = new Counter<>();
		for(int turn = 0; turn < gameSetting.getMaxWhisper(); turn++){
			Collections.shuffle(aliveWolfList);

			List<Talk> whisperList = new ArrayList<>();
			for(Agent agent:aliveWolfList){
				String whisperText = Talk.OVER;
				if(gameData.getRemainWhisperMap().get(agent) != 0){
					whisperText = gameServer.requestWhisper(agent);
				}
				if(whisperText == null || whisperText.isEmpty()){
					whisperText = Talk.SKIP;
				}
				if(whisperText == Talk.SKIP){
					skipCounter.add(agent);
					if(skipCounter.get(agent) > gameSetting.getMaxSkip()){
						whisperText = Talk.OVER;
					}
				}
				Talk whisper = new Talk(gameData.nextWhisperIdx(), gameData.getDay(), turn, agent, whisperText);
				if(!whisper.isOver() && !whisper.isSkip()){
					skipCounter.put(agent, 0);
				}
				whisperList.add(whisper);

				if(gameLogger != null){
					gameLogger.log(String.format("%d,whisper,%d,%d,%s", gameData.getDay(), whisper.getIdx(),agent.getAgentIdx(), whisper.getText()));
				}
			}
			boolean continueWhisper = false;
			for(Talk whisper:whisperList){
				gameData.addWhisper(whisper.getAgent(), whisper);
				if(gameLogger != null){
					gameLogger.log(String.format("%d,whisper,%d,%d,%d,%s", gameData.getDay(), whisper.getIdx(), whisper.getTurn(), whisper.getAgent().getAgentIdx(), whisper.getText()));
				}
				if(!whisper.isOver()){
					continueWhisper = true;
				}
			}
			if(!continueWhisper){
				break;
			}
		}
	}

	/**
	 * <div lang="ja">投票</div>
	 * 
	 * <div lang="en">Vote</div>
	 * 
	 */
	protected void vote() {
		gameData.getVoteList().clear();
		List<Agent> voters = getAliveAgentList();
		List<Agent> aliveCandidates = voters;
		for (Agent agent : voters) {
			Agent target = gameServer.requestVote(agent);
			if (gameData.getStatus(target) == Status.DEAD || target == null || agent.equals(target)) {
				target = getRandomAgent(aliveCandidates, agent);
			}
			Vote vote = new Vote(gameData.getDay(), agent, target);
			gameData.addVote(vote);
			if (gameLogger != null) {
				gameLogger.log(String.format("%d,vote,%d,%d", gameData.getDay(), vote.getAgent().getAgentIdx(), vote.getTarget().getAgentIdx()));
			}
		}
	}

	/**
	 * 
	 */
	protected void divine() {
		List<Agent> agentList = getAliveAgentList();
		for(Agent agent:getAliveAgentList()){
			if(gameData.getRole(agent) == Role.SEER){
				Agent target = gameServer.requestDivineTarget(agent);
				Role targetRole = gameData.getRole(target);
				if(gameData.getStatus(target) == Status.DEAD || target == null || targetRole == null){
//					target = getRandomAgent(agentList, agent);
				}
				else{
					Judge divine = new Judge(gameData.getDay(), agent, target, targetRole.getSpecies());
					gameData.addDivine(divine);
	
					//FOX
					if(gameData.getRole(target) == Role.FOX){
						gameData.addLastDeadAgent(target);
					}
					
					if(gameLogger != null){
						gameLogger.log(String.format("%d,divine,%d,%d,%s", gameData.getDay(), divine.getAgent().getAgentIdx(), divine.getTarget().getAgentIdx(), divine.getResult()));
					}
				}
			}
		}
	}

	/**
	 * 
	 */
	protected void guard() {
		List<Agent> agentList = getAliveAgentList();
		for(Agent agent:getAliveAgentList()){
			if(gameData.getRole(agent) == Role.BODYGUARD){
				if(agent == gameData.getBanished()){
					continue;
				}
				Agent target = gameServer.requestGuardTarget(agent);
				if(target == null || agent.equals(target)){
//					target = getRandomAgent(agentList, agent);
				}
				else{
					Guard guard = new Guard(gameData.getDay(), agent, target);
					gameData.addGuard(guard);

					if(gameLogger != null){
						gameLogger.log(String.format("%d,guard,%d,%d,%s", gameData.getDay(), guard.getAgent().getAgentIdx(), guard.getTarget().getAgentIdx(), gameData.getRole(guard.getTarget())));
					}
				}
			}
		}
	}

	protected void attack() {
		gameData.getAttackVoteList().clear();
		List<Agent> voters = getAliveWolfList();
		List<Agent> candidates = getAliveHumanList();
		for (Agent agent : voters) {
			Agent target = gameServer.requestAttackTarget(agent);
			if (gameData.getStatus(target) == Status.DEAD || gameData.getRole(target) == Role.WEREWOLF
					|| target == null) {
				// target = getRandomAgent(candidateList, agent);
			}
			else {
				Vote attackVote = new Vote(gameData.getDay(), agent, target);
				gameData.addAttack(attackVote);

				if (gameLogger != null) {
					gameLogger.log(String.format("%d,attackVote,%d,%d", gameData.getDay(),
							attackVote.getAgent().getAgentIdx(), attackVote.getTarget().getAgentIdx()));
				}
			}
		}
	}

	/**
	 * ランダムなエージェントを獲得する．ただし，withoutを除く．
	 * @param agentList
	 * @param without
	 * @return
	 */
	protected Agent getRandomAgent(List<Agent> agentList, Agent... without) {
		Agent target;
		List<Agent> list = new ArrayList<Agent>(agentList);
		for(Agent agent:without){
			list.remove(agent);
		}
		target = list.get(rand.nextInt(list.size()));
		return target;
	}

	/**
	 * get alive agents
	 * @return
	 */
	protected List<Agent> getAliveAgentList(){
		List<Agent> agentList = new ArrayList<Agent>();
		for(Agent agent:gameData.getAgentList()){
			if(gameData.getStatus(agent) == Status.ALIVE){
				agentList.add(agent);
			}
		}
		return agentList;
	}

	protected List<Agent> getAliveHumanList(){
		return gameData.getFilteredAgentList(getAliveAgentList(), Species.HUMAN);
	}

	protected List<Agent> getAliveWolfList(){
		return gameData.getFilteredAgentList(getAliveAgentList(), Species.WEREWOLF);
	}



	/**
	 * return is game finished
	 * @return
	 */
	public boolean isGameFinished() {
		Team winner = getWinner();
		return winner != null;
	}

	/**
	 * get all data of the game
	 * @return
	 */
	public GameData getGameData() {
		return gameData;
	}

	/**
	 * get setting of the game
	 * @return
	 */
	public GameSetting getGameSetting(){
		return gameSetting;
	}
	
	
	/**
	 * @return isShowConsoleLog
	 */
	public boolean isShowConsoleLog() {
		return isShowConsoleLog;
	}


	/**
	 * @param isShowConsoleLog isShowConsoleLog
	 */
	public void setShowConsoleLog(boolean isShowConsoleLog) {
		this.isShowConsoleLog = isShowConsoleLog;
	}

	/**
	 * 
	 * @param agent
	 * @return
	 */
	public String getAgentName(Agent agent){
		return agentNameMap.get(agent);
	}


}
