const { setGlobalOptions } = require("firebase-functions/v2");
const { onValueCreated } = require("firebase-functions/v2/database");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ maxInstances: 10 });

exports.sendMessageNotification = onValueCreated(
  {
    ref: "/messages/{msgId}",
    region: "us-central1"
  },
  async (event) => {
    const msg = event.data.val();
    if (!msg) return;

    const usersSnap = await admin.database().ref("users").get();
    if (!usersSnap.exists()) return;

    const tokens = [];
    usersSnap.forEach((child) => {
      const uid = child.key;
      const token = child.child("token").val();
      if (uid !== msg.uid && token) tokens.push(token);
    });

    if (tokens.length === 0) return;

    await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {
        title: msg.name || "Yeni mesaj",
        body: msg.text || (msg.imageUrl ? "📷 Fotoğraf" : "")
      },
      data: {
        name: msg.name || "",
        text: msg.text || "",
        imageUrl: msg.imageUrl || ""
      }
    });
  }
);
