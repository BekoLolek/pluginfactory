// Hand-written functional-test scenario for PingPlugin (proves the harness).
// Mirrors the shape the AI test-writer agent will later generate.
module.exports = {
  scenarios: [
    {
      name: 'ping replies pong',
      run: async (api) => {
        await api.runCommand('ping');
        await api.expectChat('pong');
      },
    },
    {
      name: 'givetoken yields a Magic Token',
      run: async (api) => {
        await api.runCommand('givetoken');
        await api.expectChat('gave token');
        await api.expectItem({ nameContains: 'Magic Token', minCount: 1 });
      },
    },
  ],
};
