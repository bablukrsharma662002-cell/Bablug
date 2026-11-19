const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCharacterPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Mia",
    skinColorHex: "#F4C29F",
    hairColorHex: "#2B1E3E",
    outfitColorHex: "#FFB020",
    pantsColorHex: "#24344D",
    accentColorHex: "#FFB020",
    hairStyle: "BOB",
    accessory: "GLASSES",
    voicePitch: 1.15,
    voiceRate: 1.0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validScenePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Park Conversation",
    backdrop: "PARK",
    durationMs: 12000,
    characters: [
      {
        characterId: "char_1",
        name: "Mia",
        x: 0.25,
        y: 0.72,
      },
    ],
    props: [
      {
        propId: "prop_1",
        type: "COFFEE_CUP",
        x: 0.5,
        y: 0.75,
      },
    ],
    interactions: [
      {
        interactionId: "int_1",
        type: "CONVERSATION",
        sourceCharacterId: "char_1",
        targetCharacterId: "char_2",
      },
    ],
    createdAt: now,
    updatedAt: now,
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

test("Unauthenticated user cannot read or write characters or scenes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("characters").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_1")
      .set(validCharacterPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scenes").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scenes")
      .doc("scene_1")
      .set(validScenePayload(ALICE_UID))
  );
});

test("Authenticated owner can create, list, get, update, and delete character templates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("characters")
    .doc("char_mia");

  await assertSucceeds(charRef.set(validCharacterPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("characters").get()
  );
  await assertSucceeds(charRef.get());

  await assertSucceeds(
    charRef.update({
      name: "Mia Director",
      accessory: "CROWN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    charRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost field shadow update
  await assertFails(
    charRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject @DocumentId "id" key on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_bad")
      .set(validCharacterPayload(ALICE_UID, { id: "char_bad" }))
  );

  // Reject invalid hex color or future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_bad2")
      .set(validCharacterPayload(ALICE_UID, { skinColorHex: "not-a-hex" }))
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_future")
      .set(
        validCharacterPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 60000),
        })
      )
  );

  await assertSucceeds(charRef.delete());
});

test("Authenticated owner can create, list, get, update, and delete animation scenes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sceneRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("scenes")
    .doc("scene_demo");

  await assertSucceeds(sceneRef.set(validScenePayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scenes").get()
  );
  await assertSucceeds(sceneRef.get());

  await assertSucceeds(
    sceneRef.update({
      title: "Cafe Meeting",
      backdrop: "CAFE",
      durationMs: 15000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid backdrop enum
  await assertFails(
    sceneRef.update({
      backdrop: "INVALID_BACKDROP",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject out-of-bounds duration
  await assertFails(
    sceneRef.update({
      durationMs: 999999,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject immutable userId hijack
  await assertFails(
    sceneRef.update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertSucceeds(sceneRef.delete());
});

test("Cross-user isolation: Bob cannot read, write, or delete Alice's characters or scenes", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_1")
      .set(validCharacterPayload(ALICE_UID));
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scenes")
      .doc("scene_1")
      .set(validScenePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("characters").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("characters")
      .doc("char_1")
      .delete()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("scenes").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scenes")
      .doc("scene_1")
      .update({
        title: "Hijacked",
        updatedAt: new Date(Date.now() - 500),
      })
  );
});
