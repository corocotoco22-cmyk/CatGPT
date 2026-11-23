const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_cat_owner";
const BOB_UID = "bob_cat_owner";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function buildCatProfile(uid, ts) {
  return {
    userId: uid,
    catName: "Mingau",
    breed: "SRD Laranja",
    totalScore: 100,
    gatonesEnabled: true,
    homeLatitude: -23.5505,
    homeLongitude: -46.6333,
    homeRadiusMeters: 150.0,
    lastLatitude: -23.5505,
    lastLongitude: -46.6333,
    lastAccuracyMeters: 8.5,
    lastZoneLabel: "Em Casa (Zona Segura)",
    gpsTrackingActive: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user: cannot read or write any CatGPT collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(buildCatProfile(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("habits").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("locations").get());
});

test("Cross-user isolation: Bob cannot read or mutate Alice's profile, habits, GPS, or translations", async () => {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).set(buildCatProfile(ALICE_UID, past));
    await db.collection("users").doc(ALICE_UID).collection("habits").doc("habit_1").set({
      userId: ALICE_UID,
      title: "Bebeu água na fonte",
      category: "HYDRATION",
      points: 15,
      completionCount: 1,
      streakDays: 1,
      positiveHabit: true,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ totalScore: 999, updatedAt: new Date(Date.now() - 1000) }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("habits").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("habits").doc("habit_1").delete());
});

test("Owner CatProfile: valid create, action-based partial updates, and shadow/future-timestamp rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildCatProfile(ALICE_UID, created))
  );

  // Action: update score
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalScore: 125,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Action: update live GPS coordinates
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      lastLatitude: -23.551,
      lastLongitude: -46.634,
      lastAccuracyMeters: 5.0,
      lastZoneLabel: "Quintal",
      gpsTrackingActive: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject Shadow Update with ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalScore: 999,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalScore: 150,
      createdAt: new Date(Date.now() - 300),
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      totalScore: 200,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Owner subcollections: habits, habit_logs, translations, locations, and chat_messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Habit create + update + list
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("h_water").set({
      userId: ALICE_UID,
      title: "Bebeu água na fonte",
      category: "HYDRATION",
      points: 15,
      completionCount: 0,
      streakDays: 0,
      positiveHabit: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("h_water").update({
      completionCount: 1,
      streakDays: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("habits").get());

  // Habit log create (and reject update)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_1").set({
      userId: ALICE_UID,
      habitId: "h_water",
      habitTitle: "Bebeu água na fonte",
      pointsDelta: 15,
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("habit_logs").doc("log_1").update({
      pointsDelta: 200,
    })
  );

  // Meow translation create + reject invalid @DocumentId `id` field
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("translations").doc("tr_1").set({
      userId: ALICE_UID,
      inputMode: "AUDIO",
      meowTranscription: "Miau-prrr!",
      humanMeaning: "Estou com fome e quero carinho.",
      gatonesReply: "Prrr-miau! Ração agora, humano!",
      detectedMood: "Faminto e Carinhoso",
      confidencePercent: 92,
      recommendedAction: "Sirva ração fresca e faça cafuné atrás das orelhas.",
      createdAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("translations").doc("tr_bad").set({
      id: "tr_bad",
      userId: ALICE_UID,
      inputMode: "AUDIO",
      meowTranscription: "Miau",
      humanMeaning: "Oi",
      gatonesReply: "Miau",
      detectedMood: "Calmo",
      confidencePercent: 80,
      recommendedAction: "Sorrir",
      createdAt: ts,
    })
  );

  // GPS point create + query
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_1").set({
      userId: ALICE_UID,
      latitude: -23.5505,
      longitude: -46.6333,
      accuracyMeters: 6.2,
      speedMps: 0.4,
      insideSafeZone: true,
      zoneLabel: "Em Casa (Zona Segura)",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").orderBy("createdAt", "desc").limit(20).get()
  );

  // Chat message create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chat_messages").doc("msg_1").set({
      userId: ALICE_UID,
      sender: "CATGPT",
      text: "Miau-olá! 🐾 Como posso ajudar seu humano hoje?",
      gatonesMode: true,
      createdAt: ts,
    })
  );
});
