// functions/index.js
const { onRequest, onCall } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

// Firebase Admin SDK'yı başlat
admin.initializeApp();

// Kullanıcı silme Cloud Function'ı
exports.deleteUser = onCall(async (request) => {
  try {
    // Sadece admin kullanıcılar çağırabilir
    const context = request.auth;

    if (!context || !context.uid) {
      throw new Error("Authentication required");
    }

    // Çağıran kullanıcının admin olup olmadığını kontrol et
    const callerDoc = await admin.firestore()
      .collection('users')
      .doc(context.uid)
      .get();

    if (!callerDoc.exists || callerDoc.data().role !== 'admin') {
      throw new Error("Admin privileges required");
    }

    const userId = request.data.userId;

    if (!userId) {
      throw new Error("User ID is required");
    }

    // Kendini silmeye çalışıyor mu kontrol et
    if (userId === context.uid) {
      throw new Error("Cannot delete yourself");
    }

    logger.info(`Admin ${context.uid} attempting to delete user ${userId}`);

    // 1. Firestore'dan kullanıcı verilerini sil
    await admin.firestore()
      .collection('users')
      .doc(userId)
      .delete();

    // 2. Kullanıcının oylarını sil
    const votesSnapshot = await admin.firestore()
      .collection('votes')
      .where('userId', '==', userId)
      .get();

    const batch = admin.firestore().batch();
    let deletedVotes = 0;

    votesSnapshot.forEach((doc) => {
      batch.delete(doc.ref);
      deletedVotes++;
    });

    if (!votesSnapshot.empty) {
      await batch.commit();
    }

    // 3. Firebase Authentication'dan kullanıcıyı sil
    try {
      await admin.auth().deleteUser(userId);
      logger.info(`Successfully deleted user ${userId} from Authentication`);
    } catch (authError) {
      logger.warn(`Could not delete user from Authentication: ${authError.message}`);
      // Authentication'dan silinemese bile devam et
    }

    logger.info(`Successfully deleted user ${userId}. Deleted ${deletedVotes} votes.`);

    return {
      success: true,
      message: `Kullanıcı başarıyla silindi`,
      deletedVotes: deletedVotes
    };

  } catch (error) {
    logger.error(`Error deleting user: ${error.message}`);
    throw new Error(`Kullanıcı silinemedi: ${error.message}`);
  }
});

// Kullanıcıyı admin yapma Cloud Function'ı
exports.makeAdmin = onCall(async (request) => {
  try {
    const context = request.auth;

    if (!context || !context.uid) {
      throw new Error("Authentication required");
    }

    // Çağıran kullanıcının admin olup olmadığını kontrol et
    const callerDoc = await admin.firestore()
      .collection('users')
      .doc(context.uid)
      .get();

    if (!callerDoc.exists || callerDoc.data().role !== 'admin') {
      throw new Error("Admin privileges required");
    }

    const userId = request.data.userId;

    if (!userId) {
      throw new Error("User ID is required");
    }

    // Kullanıcıyı admin yap
    await admin.firestore()
      .collection('users')
      .doc(userId)
      .update({
        role: 'admin'
      });

    // Custom claims ekle (isteğe bağlı)
    await admin.auth().setCustomUserClaims(userId, { admin: true });

    logger.info(`User ${userId} made admin by ${context.uid}`);

    return {
      success: true,
      message: 'Kullanıcı başarıyla admin yapıldı'
    };

  } catch (error) {
    logger.error(`Error making user admin: ${error.message}`);
    throw new Error(`Kullanıcı admin yapılamadı: ${error.message}`);
  }
});

// Sistem durumu kontrol fonksiyonu
exports.getSystemStatus = onCall(async (request) => {
  try {
    const context = request.auth;

    if (!context || !context.uid) {
      throw new Error("Authentication required");
    }

    // Admin kontrolü
    const callerDoc = await admin.firestore()
      .collection('users')
      .doc(context.uid)
      .get();

    if (!callerDoc.exists || callerDoc.data().role !== 'admin') {
      throw new Error("Admin privileges required");
    }

    // Sistem istatistikleri
    const usersSnapshot = await admin.firestore().collection('users').get();
    const electionsSnapshot = await admin.firestore().collection('elections').get();
    const votesSnapshot = await admin.firestore().collection('votes').get();

    return {
      success: true,
      data: {
        totalUsers: usersSnapshot.size,
        totalElections: electionsSnapshot.size,
        totalVotes: votesSnapshot.size,
        timestamp: new Date().toISOString()
      }
    };

  } catch (error) {
    logger.error(`Error getting system status: ${error.message}`);
    throw new Error(`Sistem durumu alınamadı: ${error.message}`);
  }
});