import axios from "../axios";
import { getApiErrorMessage } from "../utils/apiError";
import { clearStoredToken, getValidStoredToken } from "../utils/auth";
import { useState, useEffect, createContext, useCallback } from "react";

const AppContext = createContext({
  data: [],
  isError: "",
  cart: [],
  authToken: null,
  userId: null,
  role: null,
  isAdmin: false,
  isAuthenticated: false,
  addToCart: () => {},
  removeFromCart: () => {},
  updateCartItemQuantity: () => {},
  refreshData: () => {},
  login: () => {},
  logout: () => {},
  updateStockQuantity: () => {},
});

export const AppProvider = ({ children }) => {
  const [data, setData] = useState([]);
  const [isError, setIsError] = useState("");
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [cart, setCart] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem("cart")) || [];
    } catch {
      return [];
    }
  });
  const [authToken, setAuthToken] = useState(() => getValidStoredToken());
  const [userId, setUserId] = useState(() => localStorage.getItem("userId"));
  const [role, setRole] = useState(() => localStorage.getItem("role"));

  const isAuthenticated = Boolean(authToken);
  const isAdmin = role === "ADMIN";

  const clearAuthSession = useCallback(() => {
    clearStoredToken();
    setAuthToken(null);
    setUserId(null);
    setRole(null);
  }, []);

  const login = ({ jwt, userId: nextUserId, role: nextRole }) => {
    if (!jwt) {
      return;
    }

    localStorage.setItem("jwt", jwt);

    if (nextUserId !== undefined && nextUserId !== null) {
      localStorage.setItem("userId", String(nextUserId));
      setUserId(String(nextUserId));
    }

    if (nextRole) {
      localStorage.setItem("role", String(nextRole));
      setRole(String(nextRole));
    }

    setAuthToken(jwt);
  };

  const logout = () => {
    clearAuthSession();
  };

  const addToCart = (product) => {
    const existingProductIndex = cart.findIndex((item) => item.id === product.id);
    if (existingProductIndex !== -1) {
      const updatedCart = cart.map((item, index) =>
        index === existingProductIndex
          ? { ...item, quantity: item.quantity + 1 }
          : item
      );
      setCart(updatedCart);
      localStorage.setItem('cart', JSON.stringify(updatedCart));
    } else {
      const productWithoutImage = { ...product };
      delete productWithoutImage.imageData;
      const updatedCart = [...cart, { ...productWithoutImage, quantity: 1 }];
      setCart(updatedCart);
      localStorage.setItem('cart', JSON.stringify(updatedCart));
    }
  };

  const removeFromCart = (productId) => {
    const updatedCart = cart.filter((item) => item.id !== productId);
    setCart(updatedCart);
    localStorage.setItem('cart', JSON.stringify(updatedCart));
  };

  const updateCartItemQuantity = (productId, newQuantity) => {
    const updatedCart = cart.map((item) =>
      item.id === productId
        ? { ...item, quantity: newQuantity }
        : item
    );

    setCart(updatedCart);
    localStorage.setItem('cart', JSON.stringify(updatedCart));
  };

  const refreshData = useCallback(async () => {
    setIsRefreshing(true);
    try {
      const response = await axios.get(`/api/products`);
      setData(response.data);
      setIsError("");
    } catch (error) {
      setIsError(getApiErrorMessage(error, "Failed to load products"));
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  const clearCart =() =>{
    setCart([]);
  }
  
  useEffect(() => {
    refreshData();
  }, [refreshData]);

  useEffect(() => {
    if (!authToken) {
      const storedToken = getValidStoredToken();

      if (storedToken) {
        setAuthToken(storedToken);
        const storedUserId = localStorage.getItem("userId");
        const storedRole = localStorage.getItem("role");

        if (storedUserId) {
          setUserId(storedUserId);
        }

        if (storedRole) {
          setRole(storedRole);
        }
      }
    }
  }, [authToken]);

  useEffect(() => {
    const handleStorageSync = () => {
      const storedToken = getValidStoredToken();
      const storedUserId = localStorage.getItem("userId");
      const storedRole = localStorage.getItem("role");

      if (!storedToken) {
        clearAuthSession();
        return;
      }

      setAuthToken(storedToken);
      setUserId(storedUserId || null);
      setRole(storedRole || null);
    };

    const handleAuthLogout = () => {
      clearAuthSession();
    };

    window.addEventListener("storage", handleStorageSync);
    window.addEventListener("auth:logout", handleAuthLogout);

    return () => {
      window.removeEventListener("storage", handleStorageSync);
      window.removeEventListener("auth:logout", handleAuthLogout);
    };
  }, [clearAuthSession]);

  useEffect(() => {
    localStorage.setItem('cart', JSON.stringify(cart));
  }, [cart]);
  
  return (
    <AppContext.Provider value={{ data, isError, isRefreshing, cart, authToken, userId, role, isAdmin, isAuthenticated, addToCart, removeFromCart, updateCartItemQuantity, refreshData, clearCart, login, logout }}>
      {children}
    </AppContext.Provider>
  );
};

export default AppContext;