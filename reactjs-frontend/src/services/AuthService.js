// AuthService.js
const TOKEN_KEY = 'authToken';

const AuthService = {
    getToken: () => {
        return localStorage.getItem(TOKEN_KEY);
    },

    setToken: (token) => {
        if (token) {
            localStorage.setItem(TOKEN_KEY, token);
            return true;
        }
        return false;
    },

    removeToken: () => {
        localStorage.removeItem(TOKEN_KEY);
    },

    isAuthenticated: () => {
        return !!localStorage.getItem(TOKEN_KEY);
    },

    getAuthHeader: () => {
        const token = AuthService.getToken();
        return token ? { 'Authorization': `Bearer ${token}` } : {};
    }
};

export default AuthService;