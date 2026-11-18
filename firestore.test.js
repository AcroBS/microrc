const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HOST_UID = "brother_host_1";
const BRO2_UID = "brother_join_2";
const BRO3_UID = "brother_join_3";
const BRO4_UID = "brother_join_4";
const OUTSIDER_UID = "outsider_5";
const VALID_HASH = "a".repeat(64);
const WRONG_HASH = "b".repeat(64);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function makeRoomPayload(overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    hostUid: HOST_UID,
    hostName: "Alex",
    roomName: "Brothers Cup",
    roomCode: "BROS26",
    discoverable: true,
    passwordProtected: true,
    passwordSalt: "1234567890abcdef",
    passwordHash: VALID_HASH,
    status: "OPEN",
    gameMode: "RACE",
    trackId: "LIVING_ROOM_GP",
    playerCount: 1,
    maxPlayers: 4,
    memberUids: [HOST_UID],
    lobbyJson: "{\"players\":[]}",
    raceStartJson: "",
    telemetrySeq: 0,
    telemetryLine: "",
    rematchJson: "",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function makePlayerPayload(roomId, uid, overrides = {}) {
  const ts = new Date(Date.now() - 1500);
  return {
    roomId,
    userId: uid,
    name: "Driver",
    carType: "VOLT_BUGGY",
    primaryColorArgb: -43230,
    accentColorArgb: -10929,
    ready: false,
    host: false,
    rematchVote: "",
    joinToken: VALID_HASH,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write onlineRooms, leaderboardEntries, or ghostRuns", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("onlineRooms").where("discoverable", "==", true).get());
  await assertFails(unauthDb.collection("onlineRooms").doc("room_1").set(makeRoomPayload()));
  await assertFails(
    unauthDb
      .collection("leaderboardEntries")
      .where("publicEntry", "==", true)
      .where("boardKey", "==", "RACE_LIVING_ROOM_GP_3L")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("ghostRuns")
      .where("publicGhost", "==", true)
      .where("boardKey", "==", "RACE_LIVING_ROOM_GP_3L")
      .get()
  );
});

test("Online rooms: host create, filtered browser query, password verification, 4-player cap & shadow update rejection", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bro2Db = testEnv.authenticatedContext(BRO2_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Host creates password-protected room + own host player doc
  await assertSucceeds(hostDb.collection("onlineRooms").doc("room_1").set(makeRoomPayload()));
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(HOST_UID)
      .set(makePlayerPayload("room_1", HOST_UID, { host: true, ready: true }))
  );

  // Filtered browser query succeeds; unfiltered query fails
  await assertSucceeds(bro2Db.collection("onlineRooms").where("discoverable", "==", true).get());
  await assertFails(bro2Db.collection("onlineRooms").get());

  // Empty subcollections with roomId filter succeed cleanly
  await assertSucceeds(
    hostDb.collection("onlineRooms").doc("room_1").collection("inputs").where("roomId", "==", "room_1").get()
  );
  await assertSucceeds(
    hostDb.collection("onlineRooms").doc("room_1").collection("chat").where("roomId", "==", "room_1").get()
  );

  // Joining with WRONG password hash fails
  await assertFails(
    bro2Db
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(BRO2_UID)
      .set(makePlayerPayload("room_1", BRO2_UID, { joinToken: WRONG_HASH }))
  );

  // Joining with CORRECT password hash succeeds
  await assertSucceeds(
    bro2Db
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(BRO2_UID)
      .set(makePlayerPayload("room_1", BRO2_UID, { joinToken: VALID_HASH }))
  );

  // Member can post input & chat, and update own ready flag
  const ts = new Date(Date.now() - 1000);
  await assertSucceeds(
    bro2Db.collection("onlineRooms").doc("room_1").collection("players").doc(BRO2_UID).update({
      ready: true,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bro2Db.collection("onlineRooms").doc("room_1").collection("inputs").doc(BRO2_UID).set({
      roomId: "room_1",
      userId: BRO2_UID,
      seq: 1,
      throttle: 1.0,
      steer: -0.35,
      powerUpSeq: 0,
      mineDropSeq: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bro2Db.collection("onlineRooms").doc("room_1").collection("chat").doc("msg_1").set({
      roomId: "room_1",
      senderUid: BRO2_UID,
      senderName: "Brother2",
      text: "Ready to race!",
      systemMessage: false,
      createdAt: ts,
    })
  );

  // Non-host cannot overwrite room telemetry; shadow ghost field rejected
  await assertFails(
    bro2Db.collection("onlineRooms").doc("room_1").update({
      telemetrySeq: 99,
      updatedAt: ts,
    })
  );
  await assertFails(
    hostDb.collection("onlineRooms").doc("room_1").update({
      telemetrySeq: 2,
      ghostExtraField: true,
      updatedAt: ts,
    })
  );

  // Host updates room to full 4/4 -> 5th player cannot join even with valid password!
  await assertSucceeds(
    hostDb.collection("onlineRooms").doc("room_1").update({
      playerCount: 4,
      memberUids: [HOST_UID, BRO2_UID, BRO3_UID, BRO4_UID],
      updatedAt: ts,
    })
  );
  await assertFails(
    outsiderDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(OUTSIDER_UID)
      .set(makePlayerPayload("room_1", OUTSIDER_UID, { joinToken: VALID_HASH }))
  );
});

test("Leaderboards & GhostRuns: Kotlin-aligned create, partial update, filtered list & cross-user protection", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bro2Db = testEnv.authenticatedContext(BRO2_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const updated = new Date(Date.now() - 1000);
  const entryId = `${HOST_UID}_RACE_LIVING_ROOM_GP_3L`;

  await assertSucceeds(
    hostDb.collection("leaderboardEntries").doc(entryId).set({
      userId: HOST_UID,
      driverName: "Alex",
      carType: "APEX_FORMULA",
      primaryColorArgb: -43230,
      accentColorArgb: -10929,
      gameMode: "RACE",
      trackKey: "LIVING_ROOM_GP",
      boardKey: "RACE_LIVING_ROOM_GP_3L",
      totalLaps: 3,
      bestRaceTimeMs: 41250,
      bestLapTimeMs: 13100,
      bestScore: 0,
      hasGhost: true,
      ghostId: entryId,
      publicEntry: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    hostDb.collection("ghostRuns").doc(entryId).set({
      userId: HOST_UID,
      driverName: "Alex",
      carType: "APEX_FORMULA",
      primaryColorArgb: -43230,
      accentColorArgb: -10929,
      boardKey: "RACE_LIVING_ROOM_GP_3L",
      trackId: "LIVING_ROOM_GP",
      totalLaps: 3,
      finishTimeMs: 41250,
      bestLapMs: 13100,
      framesData: "0,800.0,1150.0,0.0,0.0;100,842.5,1150.0,0.02,0.0",
      publicGhost: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Brother 2 can query leaderboard & fetch ghost run for virtual challenge, but CANNOT overwrite Brother 1's record
  await assertSucceeds(
    bro2Db
      .collection("leaderboardEntries")
      .where("publicEntry", "==", true)
      .where("boardKey", "==", "RACE_LIVING_ROOM_GP_3L")
      .get()
  );
  await assertFails(bro2Db.collection("leaderboardEntries").get());
  await assertSucceeds(bro2Db.collection("ghostRuns").doc(entryId).get());
  await assertFails(
    bro2Db.collection("leaderboardEntries").doc(entryId).update({
      bestRaceTimeMs: 999999,
      updatedAt: updated,
    })
  );

  // Owner partial update succeeds when improving personal best
  await assertSucceeds(
    hostDb.collection("leaderboardEntries").doc(entryId).update({
      bestRaceTimeMs: 39800,
      bestLapTimeMs: 12750,
      updatedAt: updated,
    })
  );
});
