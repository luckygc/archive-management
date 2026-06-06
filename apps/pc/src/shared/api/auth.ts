import { request } from "./client";
import type { CurrentUserDto, LoginCommand } from "../types/auth";

export function login(command: LoginCommand) {
  return request<CurrentUserDto>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(command),
  });
}

export function getCurrentUser() {
  return request<CurrentUserDto>("/api/auth/me");
}

export function logout() {
  return request<void>("/api/auth/logout", {
    method: "POST",
  });
}
