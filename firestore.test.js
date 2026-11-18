const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validManifest(userId, backupDate = "2026-11-16", overrides = {}) {
  const serverTs = firebase.firestore.FieldValue.serverTimestamp();
  return {
    userId,
    backupDate,
    chunkCount: 1,
    totalBytes: 256,
    verified: false,
    customerCount: 2,
    transactionCount: 5,
    hasUserProfile: true,
    createdAt: serverTs,
    updatedAt: serverTs,
    ...overrides,
  };
}

function validChunk(userId, backupId, chunkIndex = 0, overrides = {}) {
  const serverTs = firebase.firestore.FieldValue.serverTimestamp();
  return {
    userId,
    backupId,
    chunkIndex,
    payload: "{\"customers\":[],\"transactions\":[]}",
    createdAt: serverTs,
    updatedAt: serverTs,
    ...overrides,
  };
}

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

test("Unauthenticated caller: cannot read or list backups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("backups").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").get());
});

test("Unauthenticated caller: cannot create backup manifest", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").set(
      validManifest(ALICE_UID)
    )
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's backups", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").set(
      validManifest(ALICE_UID)
    )
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").delete());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-17").set(
      validManifest(BOB_UID, "2026-11-17")
    )
  );
});

test("Identity spoofing: Alice cannot write manifest claiming Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").set(
      validManifest(BOB_UID)
    )
  );
});

test("Shadow update / Ghost field test: extra field on manifest create is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16").set(
      validManifest(ALICE_UID, "2026-11-16", { isAdmin: true })
    )
  );
});

test("ID poisoning & invalid date format: oversized ID or bad date fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const longId = "a".repeat(200);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc(longId).set(
      validManifest(ALICE_UID)
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_bad").set(
      validManifest(ALICE_UID, "16-11-2026")
    )
  );
});

test("Parent Gate: writing chunk without existing parent manifest fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID)
      .collection("backups").doc("backup_2026-11-16")
      .collection("chunks").doc("chunk_000")
      .set(validChunk(ALICE_UID, "backup_2026-11-16", 0))
  );
});

test("Value poisoning: oversized chunk payload (>800KB) is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const manifestRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16");
  await assertSucceeds(manifestRef.set(validManifest(ALICE_UID)));

  await assertFails(
    manifestRef.collection("chunks").doc("chunk_000").set(
      validChunk(ALICE_UID, "backup_2026-11-16", 0, { payload: "x".repeat(800005) })
    )
  );
});

test("Happy path: owner creates manifest, writes chunk, updates chunk, commits verified=true, lists & deletes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const backupRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_2026-11-16");

  // 1. Create unverified manifest
  await assertSucceeds(backupRef.set(validManifest(ALICE_UID)));

  // 2. Write chunk
  const chunkRef = backupRef.collection("chunks").doc("chunk_000");
  await assertSucceeds(chunkRef.set(validChunk(ALICE_UID, "backup_2026-11-16", 0)));

  // 3. Update chunk (same-day overwrite)
  await assertSucceeds(
    chunkRef.update({
      payload: "{\"updated\":true}",
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // 4. Commit verified = true
  await assertSucceeds(
    backupRef.update({
      verified: true,
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // 5. Same-day overwrite can reset verified=false and update counts cleanly
  await assertSucceeds(
    backupRef.update({
      chunkCount: 2,
      totalBytes: 1024,
      verified: false,
      customerCount: 3,
      transactionCount: 10,
      hasUserProfile: true,
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // 6. List manifests and chunks
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("backups").get());
  await assertSucceeds(backupRef.collection("chunks").get());

  // 7. Immutable createdAt check fails if modified
  await assertFails(
    backupRef.update({
      createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      verified: true,
      updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
    })
  );

  // 8. Owner can delete chunk & manifest (retention cleanup)
  await assertSucceeds(chunkRef.delete());
  await assertSucceeds(backupRef.delete());
});
