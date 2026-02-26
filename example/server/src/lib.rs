use spacetimedb::{ReducerContext, Table};

#[spacetimedb::table(accessor = player, public)]
pub struct Player {
    #[primary_key]
    #[auto_inc]
    id: u64,
    #[unique]
    name: String,
    score: u32,
}

#[spacetimedb::table(accessor = message, public)]
pub struct Message {
    #[primary_key]
    #[auto_inc]
    id: u64,
    sender: String,
    text: String,
}

#[spacetimedb::reducer(init)]
pub fn init(_ctx: &ReducerContext) {}

#[spacetimedb::reducer(client_connected)]
pub fn identity_connected(_ctx: &ReducerContext) {}

#[spacetimedb::reducer]
pub fn add_player(ctx: &ReducerContext, name: String) {
    ctx.db.player().insert(Player {
        id: 0,
        name,
        score: 0,
    });
}

#[spacetimedb::reducer]
pub fn set_score(ctx: &ReducerContext, player_id: u64, score: u32) {
    let mut player = ctx.db.player().id().find(player_id).expect("player not found");
    player.score = score;
    ctx.db.player().id().update(player);
}

#[spacetimedb::reducer]
pub fn send_message(ctx: &ReducerContext, sender: String, text: String) {
    ctx.db.message().insert(Message {
        id: 0,
        sender,
        text,
    });
}
