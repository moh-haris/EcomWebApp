export const isJwtLike = (token) => {
  return typeof token === "string" && token.split(".").length === 3;
};

export const getStoredToken = () => {
  return localStorage.getItem("jwt");
};

export const clearStoredToken = () => {
  localStorage.removeItem("jwt");
  localStorage.removeItem("userId");
};

export const getValidStoredToken = () => {
  const token = getStoredToken();

  if (!token) {
    return null;
  }

  if (!isJwtLike(token)) {
    clearStoredToken();
    return null;
  }

  return token;
};