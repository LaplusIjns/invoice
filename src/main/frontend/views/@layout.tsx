import { createMenuItems, useViewConfig } from '@vaadin/hilla-file-router/runtime.js';
import { effect, signal } from '@vaadin/hilla-react-signals';
import {
  AppLayout,
  DrawerToggle,
  Icon,
  SideNav,
  SideNavItem,
  Button,
  type AppLayoutElement,
  HorizontalLayout,
} from '@vaadin/react-components';
import { Suspense, useEffect, useState, useRef } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';

if ("registerProperty" in CSS) {
  const original = CSS.registerProperty.bind(CSS);

  CSS.registerProperty = ((definition: any) => {
    try {
      original(definition);
    } catch (e) {
      if (
        e instanceof DOMException &&
        e.name === "InvalidModificationError"
      ) {
        // 已經註冊過，忽略
        return;
      }
      throw e;
    }
  }) as typeof CSS.registerProperty;
}

const documentTitleSignal = signal('');
const savedTheme = localStorage.getItem('darkMode');
const darkModeSignal = signal(savedTheme === 'true');
effect(() => {
  document.title = documentTitleSignal.value;
  document.documentElement.setAttribute('theme', darkModeSignal.value ? 'dark' : 'light');
  localStorage.setItem('darkMode', darkModeSignal.value ? 'true' : 'false');
});

// Publish for Vaadin to use
(globalThis as any).Vaadin.documentTitleSignal = documentTitleSignal;

export default function MainLayout() {
  const currentTitle = useViewConfig()?.title;
  const navigate = useNavigate();
  const location = useLocation();
  const [isMobile, setIsMobile] = useState(false);
  const appLayoutRef = useRef<AppLayoutElement>(null);

  useEffect(() => {
    const query = `(max-width: 767px)`;
    const media = globalThis.matchMedia(query);
    const handleChange = () => setIsMobile(media.matches);

    setIsMobile(media.matches);
    media.addEventListener('change', handleChange);
    return () => media.removeEventListener('change', handleChange); // 清理監聽
  }, []);

  useEffect(() => {
    const savedTheme = localStorage.getItem('darkMode');
    darkModeSignal.value = savedTheme === 'true';
    if (currentTitle) {
      documentTitleSignal.value = currentTitle;
    }
  }, [currentTitle]);

  useEffect(() => {
    const appLayout = appLayoutRef.current;
    if (appLayout && isMobile) {
      appLayout.style.setProperty('--vaadin-app-layout-touch-optimized', 'true');
      (appLayout as any)._updateTouchOptimizedMode();
    }
  }, [appLayoutRef.current]);

  const toggleDarkMode = () => {
    darkModeSignal.value = !darkModeSignal.value;
  };

  return (
    <AppLayout primarySection="drawer">
      {isMobile ? (
        <HorizontalLayout slot="navbar touch-optimized" className="flex-row" style={{ width: '100%' }}>
          {createMenuItems().map(({ to, title, icon }) => (
            <SideNav onNavigate={({ path }) => navigate(path!)} location={location}>
              <SideNavItem path={to} key={to}>
                {icon ? <Icon src={icon} slot="prefix"></Icon> : <></>}
                {title}
              </SideNavItem>
            </SideNav>
          ))}
          <Button theme="contrast" onClick={toggleDarkMode}>
            <Icon src={darkModeSignal.value ? 'line-awesome/svg/sun-solid.svg' : 'line-awesome/svg/moon-solid.svg'} />
          </Button>
        </HorizontalLayout>
      ) : (
        <>
          <div slot="drawer" className="flex flex-col justify-between h-full p-m">
            <header className="flex flex-col gap-m">
              <span className="font-semibold text-l">invoice</span>
              <Button theme="contrast" onClick={toggleDarkMode}>
                <Icon
                  src={darkModeSignal.value ? 'line-awesome/svg/sun-solid.svg' : 'line-awesome/svg/moon-solid.svg'}
                />
                {darkModeSignal.value ? '明亮模式' : '黑暗模式'}
              </Button>
              <SideNav onNavigate={({ path }) => navigate(path!)} location={location}>
                {createMenuItems().map(({ to, title, icon }) => (
                  <SideNavItem path={to} key={to}>
                    {icon ? <Icon src={icon} slot="prefix"></Icon> : <></>}
                    {title}
                  </SideNavItem>
                ))}
              </SideNav>
            </header>
          </div>
          <DrawerToggle slot="navbar" aria-label="Menu toggle"></DrawerToggle>
          <h1 slot="navbar" className="text-l m-0">
            {documentTitleSignal}
          </h1>
        </>
      )}

      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
