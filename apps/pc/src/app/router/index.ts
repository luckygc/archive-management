import { createRouter, createWebHistory } from "vue-router";
import { usePageTabsStore } from "../stores/pageTabs";
import { useSessionStore } from "../stores/session";
import { routes } from "./routes";

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const sessionStore = useSessionStore();
  if (!sessionStore.initialized) {
    await sessionStore.fetchCurrentUser();
  }

  const isLoginRoute = to.name === "Login";
  if (!sessionStore.currentUser && !isLoginRoute) {
    return {
      name: "Login",
      query: {
        redirect: to.fullPath,
      },
    };
  }

  if (sessionStore.currentUser && isLoginRoute) {
    const redirect = typeof to.query.redirect === "string" ? to.query.redirect : "/";
    return redirect;
  }

  return true;
});

router.afterEach((to) => {
  const pageTabsStore = usePageTabsStore();
  pageTabsStore.openRoute(to);
});

export default router;
