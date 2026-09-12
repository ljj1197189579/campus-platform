import { createApp } from 'vue';
import axios from 'axios';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';
import App from './App.vue';
axios.defaults.baseURL = import.meta.env.VITE_API_URL || '/api';
axios.defaults.timeout = 10000;
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('campus_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
axios.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401) {
    localStorage.removeItem('campus_token');
    localStorage.removeItem('campus_user');
    window.dispatchEvent(new Event('campus-auth-expired'));
  }
  return Promise.reject(error);
});
createApp(App).use(ElementPlus, { locale: zhCn }).mount('#app');
