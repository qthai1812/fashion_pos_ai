import { createAsyncThunk } from "@reduxjs/toolkit";
import api from "@/utils/api";
import { clearStoreState } from "../store/storeSlice";

// Get user profile from JWT
export const getUserProfile = createAsyncThunk(
  "user/getProfile",
  async (token, { rejectWithValue }) => {
    try {
      const res = await api.get("/api/users/profile", {
        headers: { Authorization: `Bearer ${token}` },
      });
      return res.data;
    } catch (err) {
      return rejectWithValue(
        err.response?.data?.message || "Failed to fetch profile",
      );
    }
  },
);

// Get all customers (Khớp với CustomerController: /api/customers)
export const getCustomers = createAsyncThunk(
  "user/getCustomers",
  async (token, { rejectWithValue }) => {
    try {
      const res = await api.get("/api/customers", {
        headers: { Authorization: `Bearer ${token}` },
      });
      return res.data;
    } catch (err) {
      return rejectWithValue(
        err.response?.data?.message || "Failed to fetch customers",
      );
    }
  },
);

// Get all cashiers
export const getCashiers = createAsyncThunk(
  "user/getCashiers",
  async (token, { rejectWithValue }) => {
    try {
      const res = await api.get("/api/users/list", {
        headers: { Authorization: `Bearer ${token}` },
      });
      return res.data.filter((u) => u.role === "ROLE_BRANCH_CASHIER");
    } catch (err) {
      return rejectWithValue(
        err.response?.data?.message || "Failed to fetch cashiers",
      );
    }
  },
);

// Get all users (Đã thêm tiền tố /api để khớp với UserController)
export const getAllUsers = createAsyncThunk(
  "user/getAll",
  async (_, { rejectWithValue }) => {
    try {
      const res = await api.get("/api/users/list");
      return res.data;
    } catch (err) {
      return rejectWithValue(
        err.response?.data?.message || "Failed to fetch users",
      );
    }
  },
);

// Get user by ID (Đã thêm tiền tố /api để khớp với UserController)
export const getUserById = createAsyncThunk(
  "user/getById",
  async (userId, { rejectWithValue }) => {
    try {
      const res = await api.get(`/api/users/${userId}`);
      return res.data;
    } catch (err) {
      return rejectWithValue(err.response?.data?.message || "User not found");
    }
  },
);

// Logout user
export const logout = createAsyncThunk(
  "user/logout",
  async (_, { dispatch, rejectWithValue }) => {
    try {
      localStorage.removeItem("jwt");
      localStorage.removeItem("token");
      dispatch(clearStoreState());
      return "Logged out successfully";
    } catch (err) {
      return rejectWithValue(err.message || "Failed to logout");
    }
  },
);
