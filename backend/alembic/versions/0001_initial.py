"""initial platform schema"""

from alembic import op
import sqlalchemy as sa

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "users",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "performance_sessions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("room_code", sa.String(6), nullable=False, unique=True),
        sa.Column("status", sa.String(32), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "card_commands",
        sa.Column("id", sa.String(100), primary_key=True),
        sa.Column(
            "session_id",
            sa.String(36),
            sa.ForeignKey("performance_sessions.id"),
            nullable=False,
        ),
        sa.Column("command_type", sa.String(32), nullable=False),
        sa.Column("card_id", sa.String(32)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "reveal_tokens",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("token_hash", sa.String(64), nullable=False, unique=True),
        sa.Column("card_id", sa.String(32)),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("consumed", sa.Boolean(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "instagram_publishes",
        sa.Column("request_id", sa.String(100), primary_key=True),
        sa.Column("card_id", sa.String(32), nullable=False),
        sa.Column("publish_type", sa.String(16), nullable=False),
        sa.Column("status", sa.String(24), nullable=False),
        sa.Column("media_id", sa.String(128)),
        sa.Column("error", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "audit_events",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("event_type", sa.String(64), nullable=False),
        sa.Column("entity_id", sa.String(100), nullable=False),
        sa.Column("detail", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )


def downgrade():
    for name in (
        "audit_events",
        "instagram_publishes",
        "reveal_tokens",
        "card_commands",
        "performance_sessions",
        "users",
    ):
        op.drop_table(name)
