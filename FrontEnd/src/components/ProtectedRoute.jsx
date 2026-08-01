import React, { useContext } from "react";
import { Navigate, useLocation } from "react-router-dom";
import AppContext from "../Context/Context";

const ProtectedRoute = ({ children, requireAuth = true, requireAdmin = false }) => {
  const { authToken, isAdmin } = useContext(AppContext);
  const location = useLocation();

  if (requireAuth && !authToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default ProtectedRoute;
