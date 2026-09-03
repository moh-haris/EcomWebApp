import React, { useContext, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import AppContext from "../Context/Context";

const OAuth2Redirect = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useContext(AppContext);

  useEffect(() => {
    const token = searchParams.get("token");
    const userId = searchParams.get("userId");

    if (token) {
      login({ jwt: token, userId });
      navigate("/", { replace: true });
    } else {
      navigate("/login", { replace: true });
    }
  }, [login, navigate, searchParams]);

  return (
    <div className="container d-flex align-items-center justify-content-center" style={{ minHeight: "100vh" }}>
      <div className="text-center">
        <div className="spinner-border text-success mb-3" role="status" />
        <h5 className="mb-0">Completing sign in...</h5>
      </div>
    </div>
  );
};

export default OAuth2Redirect;
