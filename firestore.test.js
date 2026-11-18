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

function makeDriverProfilePayload(uid, name, friendCode, overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    driverName: name,
    searchName: name.toLowerCase(),
    friendCode,
    carType: "VOLT_BUGGY",
    primaryColorArgb: -43230,
    accentColorArgb: -10929,
    online: true,
    activityStatus: "ONLINE",
    activeRoomId: "",
    activeRoomCode: "",
    activeRoomName: "",
    publicProfile: true,
    lastSeenAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write any collection", async () => {
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
  await assertFails(unauthDb.collection("driverProfiles").where("publicProfile", "==", true).get());
  await assertFails(unauthDb.collection("friendships").where("memberUids", "array-contains", HOST_UID).get());
  await assertFails(unauthDb.collection("sessionInvites").where("recipientUid", "==", BRO2_UID).get());
});

test("Online rooms: host create, filtered browser query, password verification, 4-player cap & shadow update rejection", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bro2Db = testEnv.authenticatedContext(BRO2_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Pre-creation room, player, and input get() checks (resource == null) must succeed
  await assertSucceeds(hostDb.collection("onlineRooms").doc("room_1").get());
  await assertSucceeds(hostDb.collection("onlineRooms").doc("room_1").set(makeRoomPayload()));
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(HOST_UID)
      .get()
  );
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(HOST_UID)
      .set(makePlayerPayload("room_1", HOST_UID, { host: true, ready: true }))
  );
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("inputs")
      .doc(HOST_UID)
      .get()
  );
  const inputTs = new Date(Date.now() - 1200);
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_1")
      .collection("inputs")
      .doc(HOST_UID)
      .set({
        roomId: "room_1",
        userId: HOST_UID,
        seq: 0,
        throttle: 0.0,
        steer: 0.0,
        powerUpSeq: 0,
        mineDropSeq: 0,
        createdAt: inputTs,
        updatedAt: inputTs,
      })
  );

  await assertSucceeds(bro2Db.collection("onlineRooms").where("discoverable", "==", true).get());
  await assertFails(bro2Db.collection("onlineRooms").get());

  // Joiner pre-creation player get() check must succeed before joining
  await assertSucceeds(
    bro2Db
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(BRO2_UID)
      .get()
  );

  await assertFails(
    bro2Db
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(BRO2_UID)
      .set(makePlayerPayload("room_1", BRO2_UID, { joinToken: WRONG_HASH }))
  );

  await assertSucceeds(
    bro2Db
      .collection("onlineRooms")
      .doc("room_1")
      .collection("players")
      .doc(BRO2_UID)
      .set(makePlayerPayload("room_1", BRO2_UID, { joinToken: VALID_HASH }))
  );

  const ts = new Date(Date.now() - 1000);
  await assertSucceeds(
    bro2Db.collection("onlineRooms").doc("room_1").collection("players").doc(BRO2_UID).update({
      ready: true,
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

test("DriverProfiles & Friendships: online status presence, friend code search, request/accept & outsider isolation", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bro2Db = testEnv.authenticatedContext(BRO2_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Both drivers register their non-PII presence profile
  await assertSucceeds(
    hostDb.collection("driverProfiles").doc(HOST_UID).set(makeDriverProfilePayload(HOST_UID, "Alex", "RCALEX01"))
  );
  await assertSucceeds(
    bro2Db.collection("driverProfiles").doc(BRO2_UID).set(makeDriverProfilePayload(BRO2_UID, "Sam", "RCSAM002"))
  );

  // Cannot spoof another user's profile or inject extra PII field
  await assertFails(
    outsiderDb.collection("driverProfiles").doc(HOST_UID).update({
      online: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    outsiderDb.collection("driverProfiles").doc(OUTSIDER_UID).set({
      ...makeDriverProfilePayload(OUTSIDER_UID, "Eve", "RCEVE005"),
      email: "eve@example.com",
    })
  );

  // Filtered friend lookup by friendCode and whereIn userId succeeds; unfiltered list fails
  await assertSucceeds(
    hostDb
      .collection("driverProfiles")
      .where("publicProfile", "==", true)
      .where("friendCode", "==", "RCSAM002")
      .get()
  );
  await assertSucceeds(
    hostDb
      .collection("driverProfiles")
      .where("publicProfile", "==", true)
      .where("userId", "in", [BRO2_UID])
      .get()
  );
  await assertFails(hostDb.collection("driverProfiles").get());

  // Presence heartbeat partial update succeeds
  const hbTs = new Date(Date.now() - 800);
  await assertSucceeds(
    hostDb.collection("driverProfiles").doc(HOST_UID).update({
      online: true,
      activityStatus: "IN_LOBBY",
      activeRoomId: "room_BROS26",
      activeRoomCode: "BROS26",
      activeRoomName: "Brothers Cup",
      lastSeenAt: hbTs,
      updatedAt: hbTs,
    })
  );

  // Host sends friend request to Bro2
  const friendshipId = `${HOST_UID}_${BRO2_UID}`;
  const fTs = new Date(Date.now() - 1200);
  const friendshipPayload = {
    memberUids: [HOST_UID, BRO2_UID],
    requesterUid: HOST_UID,
    requesterName: "Alex",
    requesterFriendCode: "RCALEX01",
    receiverUid: BRO2_UID,
    receiverName: "Sam",
    receiverFriendCode: "RCSAM002",
    status: "PENDING",
    createdAt: fTs,
    updatedAt: fTs,
  };

  // Cannot create already ACCEPTED friendship directly (state shortcutting blocked)
  await assertFails(
    hostDb.collection("friendships").doc(friendshipId).set({
      ...friendshipPayload,
      status: "ACCEPTED",
    })
  );

  await assertSucceeds(hostDb.collection("friendships").doc(friendshipId).set(friendshipPayload));

  // Requester cannot self-accept; outsider cannot read or list
  await assertFails(
    hostDb.collection("friendships").doc(friendshipId).update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(outsiderDb.collection("friendships").doc(friendshipId).get());
  await assertFails(outsiderDb.collection("friendships").get());

  // Both members can list via memberUids array-contains
  await assertSucceeds(hostDb.collection("friendships").where("memberUids", "array-contains", HOST_UID).get());
  await assertSucceeds(bro2Db.collection("friendships").where("memberUids", "array-contains", BRO2_UID).get());

  // Receiver accepts friend request
  await assertSucceeds(
    bro2Db.collection("friendships").doc(friendshipId).update({
      status: "ACCEPTED",
      receiverName: "Sam",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Terminal state locking: cannot revert ACCEPTED back to PENDING
  await assertFails(
    bro2Db.collection("friendships").doc(friendshipId).update({
      status: "PENDING",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("SessionInvites: lobby member notifies friend, recipient observes & accepts, terminal lock & outsider denial", async () => {
  const hostDb = testEnv.authenticatedContext(HOST_UID).firestore();
  const bro2Db = testEnv.authenticatedContext(BRO2_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Create active room + host player membership first
  await assertSucceeds(hostDb.collection("onlineRooms").doc("room_BROS26").set(makeRoomPayload()));
  await assertSucceeds(
    hostDb
      .collection("onlineRooms")
      .doc("room_BROS26")
      .collection("players")
      .doc(HOST_UID)
      .set(makePlayerPayload("room_BROS26", HOST_UID, { host: true, ready: true }))
  );

  const invTs = new Date(Date.now() - 1200);
  const invitePayload = {
    senderUid: HOST_UID,
    senderName: "Alex",
    senderCarType: "VOLT_BUGGY",
    senderColorArgb: -43230,
    recipientUid: BRO2_UID,
    recipientName: "Sam",
    roomId: "room_BROS26",
    roomCode: "BROS26",
    roomName: "Brothers Cup",
    gameMode: "RACE",
    joinToken: VALID_HASH,
    status: "PENDING",
    createdAt: invTs,
    updatedAt: invTs,
  };

  // Non-member outsider cannot forge invite for room_BROS26
  await assertFails(
    outsiderDb.collection("sessionInvites").doc("inv_forged").set({
      ...invitePayload,
      senderUid: OUTSIDER_UID,
    })
  );

  // Room member sends session invite to friend
  await assertSucceeds(hostDb.collection("sessionInvites").doc("inv_1").set(invitePayload));

  // Recipient queries incoming pending invites; outsider cannot read or list
  await assertSucceeds(
    bro2Db
      .collection("sessionInvites")
      .where("recipientUid", "==", BRO2_UID)
      .where("status", "==", "PENDING")
      .get()
  );
  await assertFails(outsiderDb.collection("sessionInvites").doc("inv_1").get());
  await assertFails(outsiderDb.collection("sessionInvites").get());

  // Recipient accepts invite and joins password-protected room using invite's joinToken
  await assertSucceeds(
    bro2Db.collection("sessionInvites").doc("inv_1").update({
      status: "ACCEPTED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    bro2Db
      .collection("onlineRooms")
      .doc("room_BROS26")
      .collection("players")
      .doc(BRO2_UID)
      .set(makePlayerPayload("room_BROS26", BRO2_UID, { joinToken: VALID_HASH }))
  );

  // Terminal state lock: ACCEPTED invite cannot be mutated again
  await assertFails(
    bro2Db.collection("sessionInvites").doc("inv_1").update({
      status: "DECLINED",
      updatedAt: new Date(Date.now() - 200),
    })
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

  await assertSucceeds(
    hostDb.collection("leaderboardEntries").doc(entryId).update({
      bestRaceTimeMs: 39800,
      bestLapTimeMs: 12750,
      updatedAt: updated,
    })
  );
});
