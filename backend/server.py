#!/usr/bin/env python

import asyncio

import json
import logging
import sqlite3
from uuid import uuid4

import websockets
from sqlalchemy import create_engine
from sqlalchemy.orm import joinedload, sessionmaker
from sty import fg

from db import (
    Base,
    Game,
    GivenAnswer,
    Player,
    PlayerInGame,
    Question,
    Selected,
    Team,
    Vote,
)


conn = sqlite3.connect("quiz.db", detect_types=sqlite3.PARSE_COLNAMES)
conn.execute("PRAGMA foreign_keys = 1")


engine = create_engine("sqlite:///quiz.db", echo=True)


def init_db(session):
    Base.metadata.create_all(engine)

    # skip if there are games already
    if session.query(Game).first():
        return

    game = Game(name="Test Game", uuid="A97E57A2-E440-4975-A624-6F99999D64DA")
    questions = [Question(question="Who won the quiz?", game=game)]
    session.add(game)

    game = Game(name="new", uuid="C0CFAF27-0770-400E-A9B9-82461ED9FB6F")
    questions = [
        Question(question="Who is good at this?", game=game),
        Question(question="Who read the next question?", game=game),
    ]
    session.add(game)
    session.commit()
    t = Team(name="ee22dc", team_code="111", game_id=game.id)
    session.commit()
    p = Player(color="#ee22dc", name="p1")
    sp = PlayerInGame(team=t, player=p, game=game)
    session.add(p)
    session.commit()
    session.add(
        GivenAnswer(
            answer="This is my answer", question_uuid=questions[0].uuid, player_id=p.id
        )
    )
    session.commit()


Session = sessionmaker()

Session.configure(bind=engine)
init_db(Session())

# logging.basicConfig()

USERS = set()
USER_SESSION_MAPPING = {}
TEAM_IDS = {}
GAME_UUIDS = {}


def games_list():
    session = Session()
    games = [
        {
            "game_name": game.name,
            "game_uuid": str(game.uuid),
            "num_questions": game.num_questions,
        }
        for game in session.query(Game)
    ]
    return games


async def register(player):
    USERS.add(player)


def register_uuid(websocket, uuid):
    if uuid is None:
        uuid = str(uuid4())
    if not websocket in USER_SESSION_MAPPING:
        USER_SESSION_MAPPING[websocket] = uuid

    return uuid


async def unregister(player):
    try:
        USERS.remove(player)
    except KeyError:
        pass

    try:
        del USER_SESSION_MAPPING[player]
    except KeyError:
        pass
    try:
        del TEAM_IDS[player]
    except KeyError:
        pass
    try:
        del GAME_UUIDS[player]
    except KeyError:
        pass


HANDLERS = {}


def register_handler(fn):
    if fn.__name__ in HANDLERS:
        raise ValueError(f"Function with name {fn.name} already registered.")
    HANDLERS[fn.__name__] = fn
    return fn


def team_members(player: "PlayerConnection", session: Session):
    # Return the team memebers of a player
    db_player = player.in_db(session)
    team = session.query(Team).filter(Team.id == db_player.team_id).first()
    return team


@register_handler
async def set_name(player: "PlayerConnection", session: Session, *, player_name):
    player.set_name(session, player_name)
    sub_player = player.player_in_game(session)
    if sub_player:
        team = sub_player.team
        if team:
            await send_team_info(player, session, team=team)

    payload = {
        "player_uuid": str(player.in_db(session).uuid),
        "player_name": player.in_db(session).name,
        "player_color": player.in_db(session).color,
    }
    message = {"msg_type": "player_id", "payload": payload}
    await player.send(message)


@register_handler
async def set_color(player, session, *, color):
    player.set_color(session, color)
    sub_player = player.player_in_game(session)
    if sub_player:
        team = sub_player.team
        if team:
            await send_team_info(player, session, team=team)

    payload = {
        "player_uuid": str(player.in_db(session).uuid),
        "player_name": player.in_db(session).name,
        "player_color": player.in_db(session).color,
    }
    message = {"msg_type": "player_id", "payload": payload}
    await player.send(message)


async def notify_team(message, team_id):
    # send message to all in team

    print(f"{fg.blue}{message['msg_type']} ->> {message['payload']}{fg.rs}")
    print("sending to", list(TEAM_IDS.items()))
    json_msg = json.dumps(message)
    print(
        f"{fg.red}{len(TEAM_IDS)} TEAM_IDS. Sending to {len(list(tid for tid in TEAM_IDS.values() if tid == team_id))}{fg.rs}"
    )

    ws_remove = []

    async def send_ignore_closed(ws, msg):
        try:
            await ws.send(msg)
        except websockets.exceptions.ConnectionClosedError:
            print(f"Closing WS {ws}")
            ws_remove.append(ws)

    await asyncio.wait(
        [
            send_ignore_closed(user, json_msg)
            for user, tid in TEAM_IDS.items()
            if tid == team_id
        ]
    )
    if ws_remove:
        await asyncio.wait([unregister(ws) for ws in ws_remove])

    print(f"{fg.red}{len(TEAM_IDS)} TEAM_IDS.{fg.rs}")


async def notify_all_in_game(message, game_uuid):
    game_uuid = str(game_uuid)
    # send message to all in game)

    print(f"{fg.blue}{message['msg_type']} ->> {message['payload']}{fg.rs}")
    print("sending to", list(GAME_UUIDS.items()))
    json_msg = json.dumps(message)
    print(
        f"{fg.red}{len(GAME_UUIDS)} GAME_UUIDS. Sending to {len(list(guuid for guuid in GAME_UUIDS.values() if guuid == game_uuid))}{fg.rs}"
    )

    ws_remove = []

    async def send_ignore_closed(ws, msg):
        try:
            await ws.send(msg)
        except websockets.exceptions.ConnectionClosedError:
            print(f"Closing WS {ws}")
            ws_remove.append(ws)

    await asyncio.wait(
        [
            send_ignore_closed(user, json_msg)
            for user, guuid in GAME_UUIDS.items()
            if guuid == game_uuid
        ]
    )
    if ws_remove:
        await asyncio.wait([unregister(ws) for ws in ws_remove])

    print(f"{fg.red}{len(GAME_UUIDS)} GAME_UUIDS.{fg.rs}")


@register_handler
async def join_team(player: "PlayerConnection", session, *, team_code):
    game = player.current_game(session)
    if not game:
        return

    team = (
        session.query(Team)
        .filter(Team.team_code == team_code)
        .filter(Team.game_id == game.id)
        .first()
    )
    if team is None:
        team = Team(team_code=team_code, name="", game_id=game.id)
        session.add(team)

    sub_player = player.player_in_game(session)
    team.players.append(sub_player)

    TEAM_IDS[player.websocket] = team.id

    await send_team_info(player, session, team=team)
    await send_questions(player, session, team=team)
    await send_answers(player, session, team=team)
    await send_selected_answers(player, session, team=team)


@register_handler
async def set_team_name(player, session):
    sub_player = player.player_in_game(session)
    team = sub_player.team
    if team:
        await send_team_info(player, session, team=team)


@register_handler
async def update_answer(player, session, *, question_uuid, answer):
    sub_player = player.player_in_game(session)
    question = session.query(Question).filter(Question.uuid == question_uuid).first()
    if not question:
        print(f"{fg.red}Question {question_uuid} not found{fg.rs}")
        return
    a = (
        session.query(GivenAnswer)
        .filter(GivenAnswer.question_uuid == question.uuid)
        .filter(GivenAnswer.player_id == sub_player.id)
        .first()
    )
    if a is None:
        a = GivenAnswer(question_uuid=question_uuid, player_id=sub_player.id)
        session.add(a)
    a.answer = answer
    # Commit so that answer uuid is generated
    session.commit()
    await notify_team_of_answer(player, session, a)


async def notify_team_of_answer(player, session, answer):
    payload = {
        "answer": answer.answer,
        "question_uuid": str(answer.question_uuid),
        "answer_uuid": str(answer.uuid),
        "player_id": answer.player_id,
        "votes": [v.subplayer_id for v in answer.votes],
        "is_selected": bool(answer.is_selected),
        "timestamp": int(answer.time_created.timestamp() * 1000_000),
    }
    message = {"msg_type": "answer_changed", "payload": payload}
    await notify_team(message, TEAM_IDS[player.websocket])


def question_payload(question: Question, idx) -> dict:
    payload = {}
    payload["title"] = question.question
    payload["idx"] = idx
    payload["question_uuid"] = str(question.uuid)
    payload["is_active"] = question.is_active
    return payload


@register_handler
async def load_game(player: "PlayerConnection", session, *, game_uuid):
    payload = {}
    for game in session.query(Game).filter(Game.uuid == game_uuid):
        player.game_uuid = game_uuid
        GAME_UUIDS[player.websocket] = game_uuid

        payload = {
            "game_name": game.name,
            "game_uuid": str(game.uuid),
            "num_questions": game.num_questions,
        }

    message = {"msg_type": "init", "payload": payload}
    await player.send(message)
    # send team info sets up the team and player in game. call this first
    await send_team_info(player, session, game_uuid=game_uuid)
    await send_questions(player, session, game_uuid=game_uuid)
    await send_answers(player, session, game_uuid=game_uuid)
    await send_selected_answers(player, session, game_uuid=game_uuid)


def team_info(team, sub_player_id=None):
    payload = {
        "team_code": team.team_code,
        "team_name": team.name,
        "members": [
            {
                "player_name": player.player.name,
                "player_color": player.player.color,
                "player_id": player.id,
            }
            for player in team.players
        ],
        "quizadmin": team.quizadmin,
    }
    if sub_player_id is not None:
        payload["self_id"] = sub_player_id
    return payload


async def send_team_info(
    player: "PlayerConnection", session, *, game_uuid=None, team=None
):
    if game_uuid and team is None:
        for game in session.query(Game).filter(Game.uuid == game_uuid):
            sub_player = player.player_in_game(session)
            if sub_player and sub_player.team:
                team = sub_player.team
            else:
                team = None
    if team is not None:
        sub_player = player.player_in_game(session)
        if sub_player:
            payload = team_info(team, sub_player_id=sub_player.id)
        else:
            payload = team_info(team, sub_player_id=None)
        TEAM_IDS[player.websocket] = team.id
        message = {"msg_type": "team_id", "payload": payload}
        await notify_team(message, team.id)


async def send_questions(
    player: "PlayerConnection", session, *, game_uuid=None, team=None
):
    if game_uuid and team is None:
        for game in session.query(Game).filter(Game.uuid == game_uuid):
            pig = player.player_in_game(session)
            if pig:
                team = pig.team
            else:
                team = None
    if team is not None:
        payload = all_questions(session, game_uuid, team)
    else:
        payload = []
    if payload:
        message = {"msg_type": "set_questions", "payload": payload}
        await player.send(message)


async def send_answers(
    player: "PlayerConnection", session, *, game_uuid=None, team=None
):
    if game_uuid and team is None:
        for game in session.query(Game).filter(Game.uuid == game_uuid):
            sub_player = (
                session.query(PlayerInGame)
                .filter(PlayerInGame.game_id == game.id)
                .filter(PlayerInGame.player == player.in_db(session))
                .first()
            )
            if sub_player:
                team = sub_player.team
            else:
                team = None
    if team is not None:
        payload = all_answers(session, game_uuid, team)
    else:
        payload = []
    if payload:
        message = {"msg_type": "set_answers", "payload": payload}
        await player.send(message)


async def send_selected_answers(
    player: "PlayerConnection", session, *, game_uuid=None, team=None
):
    if game_uuid and team is None:
        for game in session.query(Game).filter(Game.uuid == game_uuid):
            sub_player = (
                session.query(PlayerInGame)
                .filter(PlayerInGame.game_id == game.id)
                .filter(PlayerInGame.player == player.in_db(session))
                .first()
            )
            if sub_player:
                team = sub_player.team
            else:
                team = None
    if team is not None and team.quizadmin:
        payload = selected_answers(session, game_uuid, team)
    else:
        payload = []
    if payload:
        message = {"msg_type": "set_selected_answers", "payload": payload}
        await player.send(message)


def selected_answers(session, game_uuid, team):
    answers = [
        {
            "answer": a.answer,
            "question_uuid": str(a.question_uuid),
            "answer_uuid": str(a.uuid),
            "team_code": a.player.team.team_code,
            # "timestamp": int(a.time_created.timestamp() * 1000_000)
        }
        for a in session.query(GivenAnswer)
        .join(GivenAnswer.player)
        .join(GivenAnswer.question)
        .join(Question.game)
        .filter(Game.uuid == game_uuid)
        .filter(GivenAnswer.is_selected == Selected.true)
        .options(joinedload(GivenAnswer.player))
    ]
    return answers


def all_questions(session, game_uuid, team):
    questions = []
    if game_uuid is not None:
        game = session.query(Game).filter(Game.uuid == game_uuid).first()
    else:
        game = team.game

    for idx, question in enumerate(game.questions):
        if question.is_active or team.quizadmin:
            questions.append(question_payload(question, idx))

    return questions


def all_answers(session, game_uuid, team):
    answers = [
        {
            "answer": a.answer,
            "question_uuid": str(a.question_uuid),
            "answer_uuid": str(a.uuid),
            "player_id": a.player.id,
            "votes": [v.subplayer_id for v in a.votes],
            "is_selected": bool(a.is_selected),
            "timestamp": int(a.time_created.timestamp() * 1000_000),
        }
        for a in session.query(GivenAnswer)
        .join(GivenAnswer.player)
        .filter(PlayerInGame.team == team)
        .options(joinedload(GivenAnswer.votes))
        .options(joinedload(GivenAnswer.player))
    ]
    return answers


@register_handler
async def select_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    # check that answer and pig have same team
    if answer.player.team == pig.team:
        # get previous selected answer
        prev_answer = (
            session.query(GivenAnswer)
            .join(GivenAnswer.player)
            .join(GivenAnswer.question)
            .join(PlayerInGame.team)
            .filter(PlayerInGame.team == pig.team)
            .filter(GivenAnswer.question == answer.question)
            .filter(GivenAnswer.is_selected == Selected.true)
            .all()
        )

        print(prev_answer)

        # update selection
        if not prev_answer or (len(prev_answer) == 1 and prev_answer[0] == answer):
            answer.is_selected = Selected.true
            await notify_team_of_answer(player, session, answer)
        else:
            answer.is_selected = Selected.true
            for prev in prev_answer:
                prev.is_selected = None
                await notify_team_of_answer(player, session, prev)
            await notify_team_of_answer(player, session, answer)


@register_handler
async def unselect_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    # check that answer and pig have same team
    if answer.player.team == pig.team:
        answer.is_selected = None
        await notify_team_of_answer(player, session, answer)


@register_handler
async def vote_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    # check that answer and pig have same team
    if answer.player.team == pig.team:
        res = (
            session.query(Vote)
            .filter(Vote.answer_id == answer.id)
            .filter(Vote.subplayer_id == pig.id)
            .first()
        )
        if res is not None:
            # already exists
            return
        vote = Vote(answer_id=answer.id, subplayer_id=pig.id)
        try:
            session.add(vote)
            await notify_team_of_answer(player, session, answer)
        except IntegrityError:
            pass


@register_handler
async def unvote_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    res = (
        session.query(Vote)
        .filter(Vote.answer_id == answer.id)
        .filter(Vote.subplayer_id == pig.id)
        .delete()
    )
    if res != 0:
        # we deleted something. report
        await notify_team_of_answer(player, session, answer)


@register_handler
async def update_question(
    player: "PlayerConnection", session, *, question_uuid, question_text
):
    question = session.query(Question).filter(Question.uuid == question_uuid).first()
    # check that question is in correct game
    if not player.game_uuid == str(question.game.uuid):
        return
    # check that player is in admin team
    pig = player.player_in_game(session)
    if pig.team and pig.team.quizadmin:
        question.question = question_text
        msg = {"msg_type": "update_question", "payload": question_payload(question, 0)}
        # TODO only notify all teams when question is published
        await notify_all_in_game(msg, question.game.uuid)


@register_handler
async def publish_question(player: "PlayerConnection", session, *, question_uuid):
    question = session.query(Question).filter(Question.uuid == question_uuid).first()
    # check that question is in correct game
    if not player.game_uuid == str(question.game.uuid):
        return
    # check that player is in admin team
    pig = player.player_in_game(session)
    if pig.team and pig.team.quizadmin:
        question.is_active = True
        msg = {"msg_type": "update_question", "payload": question_payload(question, 0)}
        await notify_all_in_game(msg, question.game.uuid)


@register_handler
async def unpublish_question(player: "PlayerConnection", session, *, question_uuid):
    question = session.query(Question).filter(Question.uuid == question_uuid).first()
    # check that question is in correct game
    if not player.game_uuid == str(question.game.uuid):
        return
    # check that player is in admin team
    pig = player.player_in_game(session)
    if pig.team and pig.team.quizadmin:
        question.is_active = False
        msg = {"msg_type": "update_question", "payload": question_payload(question, 0)}
        await notify_all_in_game(msg, question.game.uuid)


@register_handler
async def init(player, session, *, uuid=None):
    # Update session-id
    uuid = register_uuid(player, uuid)
    player.player_uuid = uuid

    p = session.query(Player).filter_by(uuid=uuid).first()
    if p is None:
        p = Player(uuid=uuid, name="", color="")
        session.add(p)

    payload = {
        "player_uuid": str(p.uuid),
        "player_name": p.name,
        "player_color": p.color,
    }
    message = {"msg_type": "player_id", "payload": payload}
    await player.send(message)


class PlayerConnection:
    def __init__(self, websocket):
        self.websocket = websocket
        self.player_uuid = None
        self.game_uuid = None

    def player_in_game(self, session) -> PlayerInGame:
        # Return (and create) the PlayerInGame object for the current player and the current game
        if not self.game_uuid:
            return None

        game = session.query(Game).filter(Game.uuid == self.game_uuid).first()

        pig = (
            session.query(PlayerInGame)
            .filter(PlayerInGame.player == self.in_db(session))
            .filter(PlayerInGame.game == game)
            .options(
                joinedload(PlayerInGame.team)
                .lazyload(Team.players)
                .joinedload(PlayerInGame.player)
            )
            .first()
        )
        if pig is None:
            pig = PlayerInGame(player=self.in_db(session), game=game)

        return pig

    def in_db(self, session) -> Player:
        return session.query(Player).filter(Player.uuid == self.player_uuid).first()

    def name(self, session):
        return (
            session.query(Player.name).filter(Player.uuid == self.player_uuid).first()
        )

    def set_name(self, session, name):
        player = self.in_db(session)
        player.name = name
        session.commit()

    def color(self, session):
        return (
            session.query(Player.color).filter(Player.uuid == self.player_uuid).first()
        )

    def set_color(self, session, color):
        player = self.in_db(session)
        player.color = color
        session.commit()

    def current_game(self, session):
        if not self.game_uuid:
            return
        return session.query(Game).filter(Game.uuid == self.game_uuid).first()

    async def send(self, message):
        print(f"{fg.blue}{message['msg_type']} -> {message['payload']}{fg.rs}")
        await self.websocket.send(json.dumps(message))

    def __str__(self):
        return f"PlayerConnection({self.websocket}, {self.player_uuid})"


async def webapp(websocket, path):
    # register(websocket) sends user_event() to websocket
    player = PlayerConnection(websocket)
    await register(player)
    session = Session()
    try:
        message = {"msg_type": "games_list", "payload": games_list()}
        await player.send(message)
        async for message in websocket:
            try:
                print(message)
                data = json.loads(message)
            except ValueError:
                print("Cannot decode message.")
                continue

            try:
                action = data.pop("action")

            except KeyError:
                print("No action in dict.")
                continue

            # player must be initialised with an uuid
            # first message must be init
            if player.player_uuid is None and action != "init":
                print(f"Not initialised yet. Ignoring message {action}.")
                continue

            if action in HANDLERS:
                handler = HANDLERS[action]
                print(f"{fg.green}{action} <- {data}{fg.rs}")
                await handler(player, session, **data)
                session.commit()

            else:
                logging.error(f"unsupported event: {data}")
    except websockets.exceptions.ConnectionClosedError as e:
        print(e)
        print(f"Closed {player}")
    finally:
        await unregister(player)


if __name__ == "__main__":
    start_server = websockets.serve(webapp, "localhost", 6789)

    asyncio.get_event_loop().run_until_complete(start_server)
    asyncio.get_event_loop().run_forever()
