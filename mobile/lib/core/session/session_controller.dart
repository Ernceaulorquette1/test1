import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../network/api_client.dart';

/// Estado de sesión de la app.
enum SessionStatus { unknown, loggedOut, needsProfile, loggedIn }

class SessionState {
  final SessionStatus status;
  final String? accessToken;
  final String? refreshToken;

  const SessionState(this.status, {this.accessToken, this.refreshToken});
}

/// ViewModel de sesión: Firebase Auth -> canje por JWT del backend.
class SessionController extends Notifier<SessionState> {
  static const _kAccess = 'access_token';
  static const _kRefresh = 'refresh_token';

  @override
  SessionState build() {
    _restore();
    return const SessionState(SessionStatus.unknown);
  }

  Future<void> _restore() async {
    final prefs = await SharedPreferences.getInstance();
    final access = prefs.getString(_kAccess);
    final refresh = prefs.getString(_kRefresh);
    state = access == null
        ? const SessionState(SessionStatus.loggedOut)
        : SessionState(SessionStatus.loggedIn, accessToken: access, refreshToken: refresh);
  }

  Future<void> signInWithGoogle() async {
    final googleUser = await GoogleSignIn().signIn();
    if (googleUser == null) return; // cancelado por el usuario
    final googleAuth = await googleUser.authentication;
    final credential = GoogleAuthProvider.credential(
      accessToken: googleAuth.accessToken,
      idToken: googleAuth.idToken,
    );
    final userCred = await FirebaseAuth.instance.signInWithCredential(credential);
    await _exchange(userCred);
  }

  Future<void> signInWithEmail(String email, String password) async {
    final cred = await FirebaseAuth.instance
        .signInWithEmailAndPassword(email: email, password: password);
    await _exchange(cred);
  }

  Future<void> registerWithEmail(String email, String password) async {
    final cred = await FirebaseAuth.instance
        .createUserWithEmailAndPassword(email: email, password: password);
    await _exchange(cred);
  }

  Future<void> sendPasswordReset(String email) =>
      FirebaseAuth.instance.sendPasswordResetEmail(email: email);

  /// Canjea el Firebase ID token por el JWT propio del backend.
  Future<void> _exchange(UserCredential cred) async {
    final idToken = await cred.user!.getIdToken();
    final api = ref.read(apiClientProvider);
    final res = await api.dio.post('/api/v1/auth/login',
        data: {'firebaseIdToken': idToken});
    final access = res.data['accessToken'] as String;
    final refresh = res.data['refreshToken'] as String;
    final profileComplete = res.data['profileComplete'] == true;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_kAccess, access);
    await prefs.setString(_kRefresh, refresh);

    state = SessionState(
      profileComplete ? SessionStatus.loggedIn : SessionStatus.needsProfile,
      accessToken: access,
      refreshToken: refresh,
    );
  }

  void completeProfile() {
    state = SessionState(SessionStatus.loggedIn,
        accessToken: state.accessToken, refreshToken: state.refreshToken);
  }

  Future<void> signOut() async {
    await FirebaseAuth.instance.signOut();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_kAccess);
    await prefs.remove(_kRefresh);
    state = const SessionState(SessionStatus.loggedOut);
  }
}

final sessionProvider =
    NotifierProvider<SessionController, SessionState>(SessionController.new);
