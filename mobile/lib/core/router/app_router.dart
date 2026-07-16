import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/login_screen.dart';
import '../../features/barcode/presentation/barcode_screen.dart';
import '../../features/chat/presentation/chat_screen.dart';
import '../../features/history/presentation/history_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/home/presentation/shell_screen.dart';
import '../../features/onboarding/presentation/onboarding_screen.dart';
import '../../features/premium/presentation/premium_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../features/profile/presentation/profile_setup_screen.dart';
import '../../features/scan/presentation/scan_screen.dart';
import '../../features/stats/presentation/stats_screen.dart';
import '../../features/water/presentation/water_screen.dart';
import '../session/session_controller.dart';

final appRouterProvider = Provider<GoRouter>((ref) {
  final session = ref.watch(sessionProvider);

  return GoRouter(
    initialLocation: '/onboarding',
    redirect: (context, state) {
      final loc = state.matchedLocation;
      switch (session.status) {
        case SessionStatus.unknown:
          return null;
        case SessionStatus.loggedOut:
          return (loc == '/onboarding' || loc == '/login') ? null : '/login';
        case SessionStatus.needsProfile:
          return loc == '/setup' ? null : '/setup';
        case SessionStatus.loggedIn:
          return (loc == '/onboarding' || loc == '/login' || loc == '/setup')
              ? '/home'
              : null;
      }
    },
    routes: [
      GoRoute(path: '/onboarding', builder: (_, __) => const OnboardingScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/setup', builder: (_, __) => const ProfileSetupScreen()),
      GoRoute(path: '/scan', builder: (_, __) => const ScanScreen()),
      GoRoute(path: '/chat', builder: (_, __) => const ChatScreen()),
      GoRoute(path: '/water', builder: (_, __) => const WaterScreen()),
      GoRoute(path: '/barcode', builder: (_, __) => const BarcodeScreen()),
      GoRoute(path: '/premium', builder: (_, __) => const PremiumScreen()),
      StatefulShellRoute.indexedStack(
        builder: (_, __, shell) => ShellScreen(shell: shell),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(path: '/home', builder: (_, __) => const HomeScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/history', builder: (_, __) => const HistoryScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/stats', builder: (_, __) => const StatsScreen()),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(path: '/profile', builder: (_, __) => const ProfileScreen()),
          ]),
        ],
      ),
    ],
  );
});
