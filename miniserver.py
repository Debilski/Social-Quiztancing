#!/usr/bin/env python

# WS server example that synchronizes state across clients

import asyncio
from collections import defaultdict
from functools import wraps
import json
import logging
import sqlite3
from uuid import UUID, uuid4
import weakref
import websockets

from sty import fg

from sqlalchemy.types import TypeDecorator, CHAR
from sqlalchemy.dialects.postgresql import UUID
import uuid

class GUID(TypeDecorator):
    """Platform-independent GUID type.

    Uses PostgreSQL's UUID type, otherwise uses
    CHAR(32), storing as stringified hex values.

    """
    impl = CHAR

    def load_dialect_impl(self, dialect):
        if dialect.name == 'postgresql':
            return dialect.type_descriptor(UUID())
        else:
            return dialect.type_descriptor(CHAR(32))

    def process_bind_param(self, value, dialect):
        if value is None:
            return value
        elif dialect.name == 'postgresql':
            return str(value)
        else:
            if not isinstance(value, uuid.UUID):
                return "%.32x" % uuid.UUID(value).int
            else:
                # hexstring
                return "%.32x" % value.int

    def process_result_value(self, value, dialect):
        if value is None:
            return value
        else:
            if not isinstance(value, uuid.UUID):
                value = uuid.UUID(value)
            return value


conn = sqlite3.connect("quiz.db", detect_types=sqlite3.PARSE_COLNAMES)
conn.execute("PRAGMA foreign_keys = 1")

USER_SESSION_MAPPING = {}

def init_db(c):
    tables = [
        """CREATE TABLE IF NOT EXISTS teams
        (
           id integer primary key,
           name text,
           ts timestamp
        )""",
        """CREATE TABLE IF NOT EXISTS users
        (
           id integer primary key,
           name text,
           color text,
           websocket text,
           team_id integer REFERENCES teams(id),
           ts timestamp
        )""",
        """CREATE TABLE IF NOT EXISTS given_answers
        (
           id integer primary key,
           answer text,
           question_id integer REFERENCES questions(id),
           user_id integer REFERENCES users(id),
           ts timestamp
        )""",
        """CREATE TABLE IF NOT EXISTS questions
           (
           id integer primary key,
           question text,
           ts timestamp
        )""",
        """CREATE TABLE IF NOT EXISTS answer_votes
           (
           id integer primary key,
           answer_id integer REFERENCES given_answers(id),
           user_id integer REFERENCES users(id),
           ts timestamp
        )""",
    ]
    for table in tables:
        c.execute(table)

# init_db(conn)

from sqlalchemy import create_engine
engine = create_engine('sqlite:///quiz.db', echo=True)

from sqlalchemy.sql import func
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.ext.hybrid import hybrid_property
from sqlalchemy.orm import relationship, joinedload, lazyload
from sqlalchemy import Table, Column, Integer, String, ForeignKey, JSON, CHAR, Boolean, UniqueConstraint, TIMESTAMP, DateTime
from sqlalchemy.exc import IntegrityError
Base = declarative_base()


class Team(Base):
    __tablename__ = 'teams'
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)
    team_code = Column(String, nullable=False)
    game_id = Column(Integer, ForeignKey('games.id'), nullable=False)
    players = relationship("PlayerInGame", back_populates="team")

    __table_args__ = (UniqueConstraint('game_id', 'team_code', name='team_code_unique_in_game'),
                     )

class Player(Base):
    __tablename__ = 'players'
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid4, unique=True)
    name = Column(String, nullable=False)
    color = Column(String, nullable=False)

    sub_players = relationship("PlayerInGame", back_populates="player")


class PlayerInGame(Base):
    __tablename__ = 'player_game'
    id = Column(Integer, primary_key=True)
    player_id = Column(Integer, ForeignKey('players.id'), nullable=False)
    game_id = Column(Integer, ForeignKey('games.id'), nullable=False)
    team_id = Column(Integer, ForeignKey('teams.id'), nullable=True)

    team = relationship("Team", back_populates="players")
    player = relationship("Player", back_populates="sub_players")
    game = relationship("Game")
    answers = relationship("GivenAnswer", back_populates="player")
    voted_for = relationship("Vote")

    __table_args__ = (UniqueConstraint('player_id', 'game_id', name='subplayer_unique_in_game'),
                     )

    def __str__(self):
        vars = "id player_id game_id team_id".split()
        return f"{self.__class__.__name__}: {' '.join([f'{v}={getattr(self, v)}' for v in vars])}"


class GivenAnswer(Base):
    __tablename__ = 'given_answers'
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid4, unique=True)
    answer = Column(String, nullable=False)
    question_uuid = Column(GUID, ForeignKey('questions.uuid'))
    player_id = Column(Integer, ForeignKey('player_game.id'))
    player = relationship("PlayerInGame", back_populates="answers")

    time_created = Column(DateTime(timezone=True), server_default=func.now())
    time_updated = Column(DateTime(timezone=True), onupdate=func.now())


    votes = relationship("Vote")

class Question(Base):
    __tablename__ = 'questions'
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid4, unique=True)
    question = Column(String, nullable=False)
    game_id = Column(Integer, ForeignKey('games.id'))
    game = relationship("Game", back_populates="questions")
    is_active = Column(Boolean, default=False)


class Vote(Base):
    __tablename__ = 'votes'
    id = Column(Integer, primary_key=True)
    answer_id = Column('answer_id', Integer, ForeignKey('given_answers.id'), index=True)
    subplayer_id = Column('subplayer_id', Integer, ForeignKey('player_game.id'))

    __table_args__ = (UniqueConstraint('subplayer_id', 'answer_id', name='subplayer_unique_in_answer'),
                     )

class Game(Base):
    __tablename__ = 'games'
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid4, unique=True)
    name = Column(String)
    questions_ordered = Column(JSON)
    questions = relationship("Question", order_by=Question.id, back_populates="game")
    num_questions = Column(Integer, default=20)


def init_db(session):
    Base.metadata.create_all(engine)

    # skip if there are games already
    if session.query(Game).first():
        return

    game = Game(name="Test Game", uuid="A97E57A2-E440-4975-A624-6F99999D64DA")
    questions = [
        Question(question="Who won the quiz?", game=game)
    ]
    session.add(game)

    game = Game(name="new", uuid="C0CFAF27-0770-400E-A9B9-82461ED9FB6F")
    questions = [
        Question(question="Who is good at this?", game=game),
        Question(question="Who read the next question?", game=game)
    ]
    session.add(game)
    session.commit()
    t = Team(name="ee22dc", team_code="111", game_id=game.id)
    session.commit()
    p = Player(color="#ee22dc", name="p1")
    sp = PlayerInGame(team=t, player=p, game=game)
    session.add(p)
    session.commit()
    session.add(GivenAnswer(answer="This is my answer", question_uuid=questions[0].uuid, player_id=p.id))
    session.commit()

from sqlalchemy.orm import sessionmaker
Session = sessionmaker()

Session.configure(bind=engine)
init_db(Session())

#logging.basicConfig()

USERS = set()


GAME_INFO = {
    "num_questions": 20,
    "questions": [
        {"title": "Question 1 Text", "answers": []},
        {"title": "Question 2 Text", "answers": []},
    ],
}

SUBSCRIPTIONS = weakref.WeakKeyDictionary()

TEAM_IDS = {}
TEAM_ANSWERS = {}
PLAYER_COLORS = weakref.WeakKeyDictionary()
PLAYER_NAMES = weakref.WeakKeyDictionary()




def games_list():
    session = Session()
    games = [{"game_name": game.name, "game_uuid": str(game.uuid)} for game in session.query(Game)]
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
    USERS.remove(player)

    del USER_SESSION_MAPPING[player]
    try:
        del TEAM_IDS[player]
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
    team = sub_player.team
    if team:
        await send_team_info(player, session, team=team)

@register_handler
async def set_color(player, session, *, color):
    player.set_color(session, color)
    sub_player = player.player_in_game(session)
    team = sub_player.team
    if team:
        await send_team_info(player, session, team=team)


async def notify_team(message, team_id):
    # send message to all in team
    print("sending to", list(TEAM_IDS.items()))
    json_msg = json.dumps(message)
    await asyncio.wait([user.send(json_msg) for user, tid in TEAM_IDS.items() if tid == team_id])


@register_handler
async def join_team(player: "PlayerConnection", session, *, team_code):
    game = player.current_game(session)
    if not game:
        return

    team = session.query(Team).filter(Team.team_code == team_code).filter(Team.game_id == game.id).first()
    if team is None:
        team = Team(team_code=team_code, name="", game_id=game.id)
        session.add(team)

    sub_player = player.player_in_game(session)
    team.players.append(sub_player)

    TEAM_IDS[player.websocket] = team.id

    await send_team_info(player, session, team=team)
    await send_answers(player, session, team=team)

@register_handler
async def set_team_name(player, session):
    sub_player = player.player_in_game(session)
    team = sub_player.team
    if team:
        await send_team_info(player, session, team=team)


@register_handler
async def update_answer(player, session, *, question_uuid, answer):
    sub_player = player.player_in_game(session)
    question = session.query(Question).filter(Question.uuid==question_uuid).first()
    if not question:
        print(f"{fg.red}Question {question_uuid} not found{fg.rs}")
        return
    a = session.query(GivenAnswer).filter(GivenAnswer.question_uuid == question.uuid).filter(GivenAnswer.player_id == sub_player.id).first()
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
        "timestamp": int(answer.time_created.timestamp() * 1000_000)
    }
    message = {"msg_type": "answer_changed", "payload": payload}
    await notify_team(message, TEAM_IDS[player.websocket])

@register_handler
async def load_game(player: "PlayerConnection", session, *, game_uuid):
    payload = {}
    for game in session.query(Game).filter(Game.uuid == game_uuid):
        player.game_uuid = game_uuid

        payload["num_questions"] = game.num_questions
        payload["questions"] = []
        for idx, question in enumerate(game.questions):
#            if not question.is_active:
#                continue
            question_payload = {}
            question_payload["title"] = question.question
            question_payload["idx"] = idx
            question_payload["question_uuid"] = str(question.uuid)

#           question_payload["answers"] = []
#           for answer in session.query(GivenAnswer).filter(GivenAnswer.question_id == question.id):
#               player = session.query(Player).filter(Player.id == answer.player_id).first()
#               answer_payload = {"answer_text": answer.answer, "answer_by": player.color}
#               question_payload["answers"].append(answer_payload)
            payload["questions"].append(question_payload)

    message = {"msg_type": "init", "payload": payload}
    await player.send(message)
    await send_team_info(player, session, game_uuid=game_uuid)
    await send_answers(player, session, game_uuid=game_uuid)


def team_info(team, sub_player_id=None):
    payload = {
        "team_code": team.team_code,
        "team_name": team.name,
        "members": [
        { "player_name": player.player.name,
            "player_color": player.player.color,
            "player_id": player.id,
        } for player in team.players]
    }
    if sub_player_id is not None:
        payload["self_id"] = sub_player_id
    return payload


async def send_team_info(player: "PlayerConnection", session, *, game_uuid=None, team=None):
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


async def send_answers(player: "PlayerConnection", session, *, game_uuid=None, team=None):
    if game_uuid and team is None:
        for game in session.query(Game).filter(Game.uuid == game_uuid):
            sub_player = session.query(PlayerInGame).filter(PlayerInGame.game_id == game.id).filter(PlayerInGame.player == player.in_db(session)).first()
            if sub_player:
                team = sub_player.team
            else:
                team = None
    if team is not None:
        payload = all_answers(session, game_uuid, team)
    else:
        payload = []
    for m in payload:
        message = {"msg_type": "answer_changed", "payload": m}
        await player.send(message)

def all_answers(session, game_uuid, team):
    answers = [
        {
            "answer": a.answer,
            "question_uuid": str(a.question_uuid),
            "answer_uuid": str(a.uuid),
            "player_id": a.player.id,
            "votes": [v.subplayer_id for v in a.votes],
            "timestamp": int(a.time_created.timestamp() * 1000_000)
    }
    for a in session.query(GivenAnswer).join(GivenAnswer.player).filter(PlayerInGame.team==team).options(joinedload(GivenAnswer.votes))]
    return answers

@register_handler
async def vote_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    # check that answer and pig have same team
    if answer.player.team == pig.team:
        res = session.query(Vote).filter(Vote.answer_id==answer.id).filter(Vote.subplayer_id==pig.id).first()
        if res is not None:
            # already exists
            return
        vote = Vote(answer_id = answer.id, subplayer_id = pig.id)
        try:
            session.add(vote)
            await notify_team_of_answer(player, session, answer)
        except IntegrityError:
            pass


@register_handler
async def unvote_answer(player: "PlayerConnection", session, *, answer_uuid):
    answer = session.query(GivenAnswer).filter(GivenAnswer.uuid == answer_uuid).first()
    pig = player.player_in_game(session)
    res = session.query(Vote).filter(Vote.answer_id==answer.id).filter(Vote.subplayer_id==pig.id).delete()
    if res != 0:
        # we deleted something. report
        await notify_team_of_answer(player, session, answer)


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
        "player_color": p.color
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

        pig = session.query(PlayerInGame).filter(PlayerInGame.player == self.in_db(session)).filter(PlayerInGame.game == game).options(joinedload(PlayerInGame.team).lazyload(Team.players).joinedload(PlayerInGame.player)).first()
        if pig is None:
            pig = PlayerInGame(player=self.in_db(session), game=game)

        return pig

    def in_db(self, session) -> Player:
        return session.query(Player).filter(Player.uuid == self.player_uuid).first()

    def name(self, session):
        return session.query(Player.name).filter(Player.uuid == self.player_uuid).first()

    def set_name(self, session, name):
        player = self.in_db(session)
        player.name = name
        session.commit()

    def color(self, session):
        return session.query(Player.color).filter(Player.uuid == self.player_uuid).first()

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
