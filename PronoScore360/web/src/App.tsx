import { Navigate, Route, Routes } from "react-router-dom";
import { RequireAdmin, RequireAuth } from "./auth/guards";
import Layout from "./components/Layout";
import AdminPanel from "./pages/admin/Admin";
import { Plans, Profile, Settings, Subscription } from "./pages/account";
import { Calendar, Home, SportSelect, Teams } from "./pages/home";
import { DeleteAccountInfo, Help, Privacy, Terms } from "./pages/legal";
import { Favorites, History, Notifications, Predictions, Selections } from "./pages/lists";
import MatchDetail from "./pages/match";
import { ForgotPassword, Login, Register, ResetPassword, Splash, VerifyEmail, Welcome } from "./pages/public";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Splash />} />
      <Route path="/welcome" element={<Welcome />} /><Route path="/login" element={<Login />} /><Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} /><Route path="/reset-password" element={<ResetPassword />} /><Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/privacy" element={<Privacy />} /><Route path="/terms" element={<Terms />} /><Route path="/help" element={<Help />} /><Route path="/delete-account" element={<DeleteAccountInfo />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/home" element={<Home />} /><Route path="/sports" element={<SportSelect />} />
        <Route path="/football" element={<Calendar sport="football" />} /><Route path="/nba" element={<Calendar sport="basketball" />} />
        <Route path="/teams" element={<Teams />} /><Route path="/match/:id" element={<MatchDetail />} />
        <Route path="/predictions" element={<Predictions />} /><Route path="/selections" element={<Selections />} />
        <Route path="/favorites" element={<Favorites />} /><Route path="/history" element={<History />} />
        <Route path="/plans" element={<Plans />} /><Route path="/subscription" element={<Subscription />} />
        <Route path="/profile" element={<Profile />} /><Route path="/settings" element={<Settings />} /><Route path="/notifications" element={<Notifications />} />
        <Route path="/admin/*" element={<RequireAdmin><AdminPanel /></RequireAdmin>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
