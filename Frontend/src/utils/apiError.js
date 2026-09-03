export const getApiErrorMessage = (error, fallbackMessage = "Something went wrong") => {
  const status = error?.response?.status;
  const responseData = error?.response?.data;

  if (responseData?.message) {
    return responseData.message;
  }

  if (responseData?.error) {
    return responseData.error;
  }

  if (status === 401 || status === 403) {
    return "Please log in first to continue.";
  }

  if (error?.message === "Network Error") {
    return "Please sign in and try again.";
  }

  return fallbackMessage;
};