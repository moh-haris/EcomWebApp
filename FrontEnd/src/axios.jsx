import axios from "axios";
import { clearStoredToken, getValidStoredToken } from "./utils/auth";

const baseURL = import.meta.env.VITE_BASE_URL || "http://localhost:8080";

const API = axios.create({
  baseURL,
});

API.interceptors.request.use((config) => {
  const token = getValidStoredToken();

  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

API.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      clearStoredToken();
      window.dispatchEvent(new CustomEvent("auth:logout"));

      if (!window.location.pathname.startsWith("/login")) {
        window.location.assign("/login");
      }
    }

    if (error?.response?.status === 403) {
      window.alert("Access Denied: You do not have permission to perform this action.");
    }

    return Promise.reject(error);
  }
);

export default API;
