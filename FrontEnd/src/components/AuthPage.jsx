import React, { useContext, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import API from "../axios";
import { getApiErrorMessage } from "../utils/apiError";
import AppContext from "../Context/Context";

const AuthPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { login } = useContext(AppContext);
  const [mode, setMode] = useState("login");
  const [form, setForm] = useState({ username: "", password: "", name: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    const errorParam = searchParams.get("error");

    if (errorParam) {
      setError(decodeURIComponent(errorParam));
    }
  }, [searchParams]);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");

    if (mode === "register" && !/^[a-zA-Z\s]+$/.test(form.name.trim())) {
      setError("Name must contain only letters and spaces");
      setLoading(false);
      return;
    }

    const payload =
      mode === "login"
        ? { username: form.username, password: form.password }
        : { username: form.username, password: form.password, name: form.name };

    try {
      const response = await API.post(
        mode === "login" ? "/auth/login" : "/auth/signup",
        payload
      );

      const token = response.data?.jwt || response.data?.token || response.data?.accessToken || response.data;
      const responseUserId = response.data?.userId ?? response.data?.id ?? null;
      const responseRole = response.data?.role ?? null;

      if (token) {
        login({ jwt: token, userId: responseUserId, role: responseRole });
        navigate("/");
      } else {
        setError("No token returned from the backend.");
      }
    } catch (authError) {
      const responseData = authError?.response?.data;
      const responseMessage = responseData?.message || responseData?.error || "";

      if (mode === "login" && /bad credentials/i.test(responseMessage)) {
        setError("User not found: signup");
      } else {
        setError(getApiErrorMessage(authError, "Authentication failed"));
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSignIn = () => {
    window.location.href = "http://localhost:8080/oauth2/authorization/google";
  };

  return (
    <div
      className="min-vh-100 d-flex align-items-center justify-content-center py-5"
      style={{
        background:
          "radial-gradient(circle at top, rgba(25, 135, 84, 0.12), transparent 36%), linear-gradient(180deg, #f8fbff 0%, #eef4f8 100%)",
        paddingTop: "5rem",
      }}
    >
      <div className="container">
        <div className="row justify-content-center">
          <div className="col-12 col-lg-10 col-xl-8">
            <div className="card border-0 shadow-lg overflow-hidden" style={{ borderRadius: "1.5rem" }}>
              <div className="row g-0">
                <div
                  className="col-md-5 text-white p-4 p-md-5 d-flex flex-column justify-content-between"
                  style={{ background: "linear-gradient(160deg, #198754 0%, #0f5132 100%)" }}
                >
                  <div>
                    <div className="badge bg-white text-success rounded-pill px-3 py-2 mb-4">
                      Telusko Store
                    </div>
                    <h2 className="fw-bold mb-3">{mode === "login" ? "Welcome back" : "Join the store"}</h2>
                    <p className="mb-0 text-white-50">
                      {mode === "login"
                        ? "Sign in to manage your cart, orders, and products."
                        : "Create an account to get started with faster checkout and account access."}
                    </p>
                  </div>

                  <div className="mt-4 small text-white-50">
                    Secure sign-in with manual login or Google OAuth2.
                  </div>
                </div>

                <div className="col-md-7 bg-white p-4 p-md-5">
                  <div className="text-center text-md-start mb-4">
                    <h3 className="fw-bold mb-2">{mode === "login" ? "Login to your account" : "Sign up your account"}</h3>
                    <p className="text-muted mb-0">Use your username and password, or continue with Google.</p>
                  </div>

                  {error && <div className="alert alert-danger">{error}</div>}

                  <form onSubmit={handleSubmit} className="d-grid gap-3">
                    <div>
                      <label className="form-label fw-semibold">Username</label>
                      <input
                        type="text"
                        name="username"
                        className="form-control form-control-lg"
                        placeholder="Enter username"
                        value={form.username}
                        onChange={handleChange}
                        required
                      />
                    </div>

                    {mode === "register" && (
                      <div>
                        <label className="form-label fw-semibold">Name</label>
                        <input
                          type="text"
                          name="name"
                          className="form-control form-control-lg"
                          placeholder="Enter your name"
                          value={form.name}
                          onChange={handleChange}
                          pattern="^[a-zA-Z\s]+$"
                          title="Name must contain only letters and spaces"
                          required
                        />
                      </div>
                    )}

                    <div>
                      <label className="form-label fw-semibold">Password</label>
                      <div className="input-group input-group-lg">
                        <input
                          type={showPassword ? "text" : "password"}
                          name="password"
                          className="form-control"
                          placeholder="Enter password"
                          value={form.password}
                          onChange={handleChange}
                          required
                        />
                        <button
                          type="button"
                          className="btn btn-outline-secondary"
                          onClick={() => setShowPassword((current) => !current)}
                          aria-label={showPassword ? "Hide password" : "Show password"}
                        >
                          <i className={`bi ${showPassword ? "bi-eye-slash" : "bi-eye"}`}></i>
                        </button>
                      </div>
                    </div>

                    <button className="btn btn-success btn-lg fw-semibold" type="submit" disabled={loading}>
                      {loading ? "Please wait..." : mode === "login" ? "Login" : "Sign Up"}
                    </button>
                  </form>

                  <div className="d-flex align-items-center my-4">
                    <div className="flex-grow-1 border-top"></div>
                    <span className="px-3 text-muted small">OR</span>
                    <div className="flex-grow-1 border-top"></div>
                  </div>

                  <button
                    type="button"
                    className="btn btn-outline-dark btn-lg w-100 d-flex align-items-center justify-content-center gap-2"
                    onClick={handleGoogleSignIn}
                  >
                    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
                      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 33 29.4 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.2 6.3 29.4 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.7-.2-2.9-.4-3.5z" />
                      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 16 18.9 12 24 12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.2 6.3 29.4 4 24 4 16.3 4 9.6 8.4 6.3 14.7z" />
                      <path fill="#4CAF50" d="M24 44c5.2 0 10-2 13.6-5.2l-6.3-5.3C29.4 35 26.9 36 24 36c-5.4 0-9.7-3-11.3-7.5l-6.6 5.1C9.3 39.6 16 44 24 44z" />
                      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-1 2.8-2.9 5-5.4 6.5l.1-.1 6.3 5.3C35.9 38.9 40 32 40 24c0-1.7-.2-2.9-.4-3.5z" />
                    </svg>
                    Sign in with Google
                  </button>

                  <div className="text-center mt-4">
                    <button
                      type="button"
                      className="btn btn-link text-decoration-none fw-semibold"
                      onClick={() => {
                        setMode(mode === "login" ? "register" : "login");
                        setError("");
                      }}
                    >
                      {mode === "login" ? "Need an account? Signup" : "Already have an account? Login"}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AuthPage;
