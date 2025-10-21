"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = __importDefault(require("express"));
const cors_1 = __importDefault(require("cors"));
const index_1 = __importDefault(require("./routes/index"));
const scheduler_1 = require("./scheduler");
const app = (0, express_1.default)();
// CORS middleware
app.use((0, cors_1.default)({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Origin', 'X-Requested-With', 'Content-Type', 'Accept', 'Authorization']
}));
// Middleware pentru logging
app.use((req, res, next) => {
    console.log(`${req.method} ${req.url}`, req.body);
    next();
});
// Body parser middleware
app.use(express_1.default.json());
app.use(express_1.default.urlencoded({ extended: true }));
// Routes
app.use('/', index_1.default);
// Error handling middleware
app.use((err, req, res, next) => {
    console.error('Error:', err);
    res.status(500).json({ message: 'Eroare internă la server', error: err.message });
});
// După ce pornești serverul Express:
(0, scheduler_1.startScheduler)();
exports.default = app;
//test