import { Navigate, Route, Routes } from 'react-router-dom'
import Login from './routes/Login'
import GroupsList from './routes/GroupsList'
import GroupDetail from './routes/GroupDetail'
import CreateGroup from './routes/CreateGroup'
import Profile from './routes/Profile'

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/groups" element={<GroupsList />} />
      <Route path="/groups/new" element={<CreateGroup />} />
      <Route path="/groups/:groupId" element={<GroupDetail />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="/" element={<Navigate to="/groups" replace />} />
    </Routes>
  )
}

export default App
