import { BrowserRouter, Routes, Route } from 'react-router-dom'


import Login from '../pages/Login'
import OAuthCallback from '../pages/OAuthCallback'
import Home from '../pages/Home'
import ActivityList from '../pages/ActivityList'
import ActivityDetail from '../pages/ActivityDetail'
import CreateActivity from '../pages/CreateActivity'
import MyActivities from '../pages/MyActivities'
import UserProfile from '../pages/UserProfile'
import OrganizerDashboard from '../pages/OrganizerDashboard'
import SignupManagement from '../pages/SignupManagement'
import MyFavorites from '../pages/MyFavorites'
import EditActivity from '../pages/EditActivity'
import CheckIn from '../pages/CheckIn'
import Feedback from '../pages/Feedback'
import OrganizerAnalytics from '../pages/OrganizerAnalytics'
import CommunityClusters from '../pages/CommunityClusters'
import AdminDashboard from '../pages/AdminDashboard'
import AdminCommunityClustering from '../pages/AdminCommunityClustering'

export default function Router() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/oauth/callback" element={<OAuthCallback />} />
        <Route path="/home" element={<Home />} />
        <Route path="/activities" element={<ActivityList />} />
        <Route path="/activity/:id" element={<ActivityDetail />} />
        <Route path="/create" element={<CreateActivity />} />
        <Route path="/my" element={<MyActivities />} />
        <Route path="/profile" element={<UserProfile />} />
        <Route path="/organizer" element={<OrganizerDashboard />} />
        <Route path="/signup-management" element={<SignupManagement />} />
        <Route path="/favorites" element={<MyFavorites />} />
        <Route path="/edit/:id" element={<EditActivity />} />
        <Route path="/checkin" element={<CheckIn />} />
        <Route path="/feedback" element={<Feedback />} />
        <Route path="/organizer-analytics" element={<OrganizerAnalytics />} />
        <Route path="/community" element={<CommunityClusters />} />
        <Route path="/admin" element={<AdminDashboard />} />
        <Route path="/admin/community-clustering" element={<AdminCommunityClustering />} />
      </Routes>
    </BrowserRouter>
  )
}
