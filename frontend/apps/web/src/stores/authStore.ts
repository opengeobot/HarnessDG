import { create } from 'zustand';

interface UserInfo {
  id: number;
  username: string;
  displayName: string;
  email: string;
  avatar: string;
  preferredLocale: string;
  roles: string[];
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: UserInfo) => void;
  updateUser: (user: UserInfo) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => {
  // 从 localStorage 恢复用户信息
  const token = localStorage.getItem('access_token');
  const userStr = localStorage.getItem('user_info');
  const user = userStr ? JSON.parse(userStr) : null;

  return {
    token,
    user,
    isAuthenticated: !!token,
    setAuth: (token, user) => {
      localStorage.setItem('access_token', token);
      localStorage.setItem('user_info', JSON.stringify(user));
      set({ token, user, isAuthenticated: true });
    },
    updateUser: (user) => {
      localStorage.setItem('user_info', JSON.stringify(user));
      set({ user });
    },
    logout: () => {
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      localStorage.removeItem('user_info');
      set({ token: null, user: null, isAuthenticated: false });
    },
  };
});
