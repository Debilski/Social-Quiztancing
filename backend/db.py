import enum
import uuid

from sqlalchemy import (
    CHAR,
    JSON,
    Boolean,
    Column,
    DateTime,
    Enum,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from sqlalchemy.types import CHAR, TypeDecorator


class GUID(TypeDecorator):
    """Platform-independent GUID type.

    Uses PostgreSQL's UUID type, otherwise uses
    CHAR(32), storing as stringified hex values.

    """

    impl = CHAR

    def load_dialect_impl(self, dialect):
        if dialect.name == "postgresql":
            return dialect.type_descriptor(UUID())
        else:
            return dialect.type_descriptor(CHAR(32))

    def process_bind_param(self, value, dialect):
        if value is None:
            return value
        elif dialect.name == "postgresql":
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


Base = declarative_base()


class Team(Base):
    __tablename__ = "teams"
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)
    team_code = Column(String, nullable=False)
    game_id = Column(Integer, ForeignKey("games.id"), nullable=False)
    players = relationship("PlayerInGame", back_populates="team")

    quizadmin = Column(Boolean, default=False, nullable=False)

    __table_args__ = (
        UniqueConstraint("game_id", "team_code", name="team_code_unique_in_game"),
    )


class Player(Base):
    __tablename__ = "players"
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid.uuid4, unique=True)
    name = Column(String, nullable=False)
    color = Column(String, nullable=False)

    sub_players = relationship("PlayerInGame", back_populates="player")


class PlayerInGame(Base):
    __tablename__ = "player_game"
    id = Column(Integer, primary_key=True)
    player_id = Column(Integer, ForeignKey("players.id"), nullable=False)
    game_id = Column(Integer, ForeignKey("games.id"), nullable=False)
    team_id = Column(Integer, ForeignKey("teams.id"), nullable=True)

    team = relationship("Team", back_populates="players")
    player = relationship("Player", back_populates="sub_players")
    game = relationship("Game")
    answers = relationship("GivenAnswer", back_populates="player")
    voted_for = relationship("Vote")

    __table_args__ = (
        UniqueConstraint("player_id", "game_id", name="subplayer_unique_in_game"),
    )

    def __str__(self):
        vars = "id player_id game_id team_id".split()
        return f"{self.__class__.__name__}: {' '.join([f'{v}={getattr(self, v)}' for v in vars])}"


class Selected(enum.Enum):
    true = True


class GivenAnswer(Base):
    __tablename__ = "given_answers"
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid.uuid4, unique=True)
    answer = Column(String, nullable=False)
    question_uuid = Column(GUID, ForeignKey("questions.uuid"))
    player_id = Column(Integer, ForeignKey("player_game.id"))
    player = relationship("PlayerInGame", back_populates="answers")
    question = relationship("Question")

    time_created = Column(DateTime(timezone=True), server_default=func.now())
    time_updated = Column(DateTime(timezone=True), onupdate=func.now())

    votes = relationship("Vote")
    is_selected = Column(Enum(Selected))
    __table_args__ = (UniqueConstraint("question_uuid", "player_id", "is_selected"),)


class Question(Base):
    __tablename__ = "questions"
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid.uuid4, unique=True)
    question = Column(String, nullable=False)
    game_id = Column(Integer, ForeignKey("games.id"))
    game = relationship("Game", back_populates="questions")
    is_active = Column(Boolean, default=False)


class Vote(Base):
    __tablename__ = "votes"
    id = Column(Integer, primary_key=True)
    answer_id = Column("answer_id", Integer, ForeignKey("given_answers.id"), index=True)
    subplayer_id = Column("subplayer_id", Integer, ForeignKey("player_game.id"))

    __table_args__ = (
        UniqueConstraint(
            "subplayer_id", "answer_id", name="subplayer_unique_in_answer"
        ),
    )


class Game(Base):
    __tablename__ = "games"
    id = Column(Integer, primary_key=True)
    uuid = Column(GUID, default=uuid.uuid4, unique=True)
    name = Column(String)
    questions_ordered = Column(JSON)
    questions = relationship("Question", order_by=Question.id, back_populates="game")
    num_questions = Column(Integer, default=20)

    teams = relationship("Team", backref="game")
