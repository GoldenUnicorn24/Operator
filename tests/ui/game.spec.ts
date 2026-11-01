import { test, expect, type Page } from "@playwright/test";
import { readFileSync, mkdirSync } from "node:fs";
const regions: { id: string; name: string }[] = JSON.parse(
  readFileSync("web/data/geo/germany-states.json", "utf8"),
);
const evidence = "docs/evidence";
mkdirSync(evidence, { recursive: true });
async function setup(
  page: Page,
  mode = "sandbox",
  region = "NI",
  preset = "early",
  hour = "6",
) {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.route("**/*", (route) =>
    new URL(route.request().url()).hostname === "127.0.0.1"
      ? route.continue()
      : route.abort(),
  );
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("data-app-ready", "true");
  await page.locator(`[data-mode="${mode}"]`).click();
  await page.getByRole("button", { name: "Spielmodus wählen" }).click();
  await page.locator("#planRegion").selectOption(region);
  await page.locator("#planShift").selectOption(preset);
  await page.locator("#planStart").selectOption(hour);
  return errors;
}
async function advance(page: Page, n: number) {
  await page.evaluate((n) => (window as any).EL110.advance(n), n);
}
test("offline statewide play: Niedersachsen → live dispatch → retained Bayern → complete 24h report → next day", async ({
  page,
}) => {
  const errors = await setup(page, "sandbox", "NI", "full24");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `${evidence}/shift-choice-24h.png` });
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await expect(page.locator('[data-boundary="NI"]')).toBeVisible();
  await page
    .getByRole("button", { name: "Verteilte Notrufe auslösen" })
    .click();
  await expect(page.locator(".map-legend")).toContainText("32 / 32");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: `${evidence}/niedersachsen-overview.png`,
    fullPage: true,
  });
  await page.locator(".map-list-all").click();
  await page.getByRole("button", { name: /Notruf · Hannover.*P1/ }).click();
  await expect(page.locator("#mapDetail")).toContainText("Notruf · Hannover");
  const uid = await page.evaluate(() => {
    const s = (window as any).EL110.getState();
    return s.units.filter(
      (u: any) => u.type === "STREIFE" && u.regionId === "NI",
    )[1].id;
  });
  await page.locator("#mapDispatch").selectOption(String(uid));
  await page
    .locator("#mapDetail")
    .getByRole("button", { name: "Entsenden", exact: true })
    .click();
  const origin = await page.evaluate(
    (id) =>
      (window as any).EL110.getState().units.find((u: any) => u.id === id)
        .position,
    uid,
  );
  await advance(page, 10);
  const moved = await page.evaluate(
    (id) =>
      (window as any).EL110.getState().units.find((u: any) => u.id === id)
        .position,
    uid,
  );
  expect(moved).not.toEqual(origin);
  await page.locator(".map-fit").click();
  await page.locator("#pauseButton").click();
  const paused = await page.evaluate(
    () => (window as any).EL110.getState().absoluteSimMinutes,
  );
  await advance(page, 20);
  expect(
    await page.evaluate(
      () => (window as any).EL110.getState().absoluteSimMinutes,
    ),
  ).toBe(paused);
  const frozenPosition = await page.evaluate(
    (id) =>
      (window as any).EL110.getState().units.find((u: any) => u.id === id)
        .position,
    uid,
  );
  await page.locator("#liveRegion").selectOption("BY");
  await expect(page.locator('[data-boundary="BY"]')).toBeVisible();
  await page
    .getByRole("button", { name: "Verteilte Notrufe auslösen" })
    .click();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: `${evidence}/bayern-overview.png`,
    fullPage: true,
  });
  await page.locator("#liveRegion").selectOption("NI");
  expect(
    await page.evaluate(
      (id) =>
        (window as any).EL110.getState().units.find((u: any) => u.id === id)
          .position,
      uid,
    ),
  ).toEqual(frozenPosition);
  await page.locator("#pauseButton").click();
  await advance(page, 1440);
  await expect(page.locator("#modalBody")).toContainText("Tagesauswertung");
  await expect(page.locator("#modalBody")).toContainText("1440 Minuten");
  await expect(page.locator("#modalBody")).toContainText(
    "Interne Personalwechsel",
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `${evidence}/24h-report.png`, fullPage: true });
  await page.getByRole("button", { name: "Nächsten Tag vorbereiten" }).click();
  await expect(page.locator("#modalBody")).toContainText("TAG 2");
  await page.reload();
  await expect(page.locator("#modalBody")).toContainText("TAG 2");
  expect(errors).toEqual([]);
});
test("full_state_bounds_each_region: each real full boundary fits into mobile canvas offline", async ({
  page,
}) => {
  const errors = await setup(page);
  await page
    .getByRole("button", { name: "Karte ansehen", exact: true })
    .click();
  for (const r of regions) {
    await page.locator("#liveRegion").selectOption(r.id);
    const boundary = page.locator(`[data-boundary="${r.id}"]`);
    await expect(boundary).toBeVisible();
    const bounds = await boundary.boundingBox(),
      canvas = await page.locator(".map-canvas").boundingBox();
    expect(bounds).not.toBeNull();
    expect(bounds!.x).toBeGreaterThanOrEqual(canvas!.x + 20);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(
      canvas!.x + canvas!.width - 20,
    );
    expect(bounds!.y).toBeGreaterThanOrEqual(canvas!.y + 20);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(
      canvas!.y + canvas!.height - 20,
    );
    await expect(page.locator(".map-legend")).toContainText("32 / 32");
  }
  expect(errors).toEqual([]);
});
test("Hamburg live streets, filters, clusters, touch selection, editor location and portrait management", async ({
  page,
}) => {
  const errors = await setup(page, "sandbox", "HH");
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Verteilte Notrufe auslösen" })
    .click();
  await page
    .getByRole("button", { name: "Hamburg Stadtgebiet", exact: true })
    .click();
  await expect(page.locator(".map-road").first()).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: `${evidence}/hamburg-live.png`,
    fullPage: true,
  });
  await page.locator('[data-filter="type"]').selectOption("SEK");
  await page.locator(".map-list-all").click();
  await expect(page.locator(".map-members")).toContainText(
    "Spezialeinsatzkommando",
  );
  await page.locator('[data-filter="type"]').selectOption("all");
  await page.locator(".map-fit").click();
  const cluster = page.locator("[data-cluster]").first();
  await cluster.click();
  await expect(
    page.getByRole("button", { name: "Zum Cluster zoomen" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Zum Cluster zoomen" }).click();
  await page.locator('[data-tab="units"]').click();
  await page.locator("#unitList button").first().click();
  await page.locator(".officer-tile").first().click();
  await expect(page.locator("#modalBody")).toContainText("Beamtenprofil");
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({
    path: `${evidence}/personnel-equipment.png`,
    fullPage: true,
  });
  await page.getByRole("button", { name: "280 € kaufen" }).click();
  await expect(page.locator("#modalBody")).toContainText(
    "Inventar & Ausrüstung · 3/4",
  );
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await page.locator('[data-tab="editor"]').click();
  await page.locator("#eName").fill("Hafen-Nachkontrolle");
  await page.locator("#eLat").fill("53.5511");
  await page.locator("#eLon").fill("9.9937");
  await page.getByRole("button", { name: "Einsatzvorlage erstellen" }).click();
  await expect(page.locator("#templateList")).toContainText(
    "Hafen-Nachkontrolle",
  );
  await page.locator('[data-tab="history"]').click();
  await page.getByRole("checkbox", { name: "Stumm", exact: true }).check();
  await page.getByRole("button", { name: "Funkprobe" }).click();
  expect(errors).toEqual([]);
});
for (const mode of ["career", "sandbox"])
  for (const [preset, hours, hour] of [
    ["night", 8, "22"],
    ["night12", 12, "18"],
    ["full24", 24, "6"],
  ] as const)
    test(`shift_${hours}h ${mode}: actual UI choice, midnight, report, continuation`, async ({
      page,
    }) => {
      const errors = await setup(page, mode, "HH", preset, hour);
      if (mode === "sandbox")
        await page.getByLabel("Automatische Notrufe").uncheck();
      await page
        .getByRole("button", { name: "Schicht beginnen", exact: true })
        .click();
      await advance(page, hours * 60);
      await expect(page.locator("#modalBody")).toContainText("Tagesauswertung");
      await expect(page.locator("#modalBody")).toContainText(
        `${hours * 60} Minuten`,
      );
      await page
        .getByRole("button", { name: "Nächsten Tag vorbereiten" })
        .click();
      expect(
        await page.evaluate(() => (window as any).EL110.getState().day),
      ).toBe(2);
      expect(errors).toEqual([]);
    });
test("alpha migration via old storage, sanitized upload, invalid import preserves current save and landscape navigation", async ({
  page,
}) => {
  const fixture = readFileSync("tests/fixtures/alpha02.json", "utf8");
  await page.addInitScript(
    (raw) => localStorage.setItem("el110-save-v2", raw),
    fixture,
  );
  await page.goto("/");
  await expect(page.locator("html")).toHaveAttribute("data-app-ready", "true");
  expect(
    await page.evaluate(() => (window as any).EL110.getState().units.length),
  ).toBe(6);
  await page.getByRole("button", { name: "Personal & Dienstplan" }).click();
  await page.locator("#unitList button").first().click();
  await page.locator(".officer-tile").first().click();
  await page.locator("#modalBody input[type=file]").setInputFiles({
    name: "portrait.png",
    mimeType: "image/png",
    buffer: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/b6kAAAAASUVORK5CYII=",
      "base64",
    ),
  });
  await expect
    .poll(() =>
      page.evaluate(
        () => (window as any).EL110.getState().units[0].officers[0].photo,
      ),
    )
    .toMatch(/^data:image\/jpeg;base64,/);
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await page.locator('[data-tab="editor"]').click();
  const before = await page.evaluate(() =>
    JSON.stringify((window as any).EL110.getState()),
  );
  await page.locator("#importFile").setInputFiles({
    name: "bad.json",
    mimeType: "application/json",
    buffer: Buffer.from('{"version":3}'),
  });
  await expect(page.locator("#toast")).toBeVisible();
  expect(
    await page.evaluate(() => JSON.stringify((window as any).EL110.getState())),
  ).toBe(before);
  await page.setViewportSize({ width: 915, height: 412 });
  await page.locator('[data-tab="dispatch"]').click();
  await expect(page.locator(".map-fit")).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: `${evidence}/landscape.png`, fullPage: true });
  await page.reload();
  expect(
    await page.evaluate(
      () => (window as any).EL110.getState().units[0].officers[0].photo,
    ),
  ).toMatch(/^data:image\/jpeg;base64,/);
});

test("editor chain, personnel training and vehicle changes persist through two full shifts", async ({
  page,
}) => {
  const errors = await setup(page, "sandbox", "NI");
  await page.getByRole("button", { name: "Personal & Dienstplan" }).click();
  await page.locator("#unitList button").first().click();
  await page
    .locator(".listrow")
    .filter({ hasText: "Kommunikation & Deeskalation" })
    .getByRole("button", { name: "Beginnen" })
    .click();
  await expect(page.locator("#modalBody")).toContainText(
    "noch 2 abgeschlossene Spieltage",
  );
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await page.locator('[data-tab="editor"]').click();
  await page.locator("#vName").fill("Landesstreife");
  await page.locator("#vSpeed").fill("75");
  await page
    .getByRole("button", { name: "Einheit/Fahrzeug speichern" })
    .click();
  await expect(page.locator("#toast")).toContainText("gespeichert");
  const template = {
    id: 0,
    name: "Eigene Einsatzkette",
    description: "Hinweis prüfen und Nachkontrolle disponieren.",
    priority: 3,
    needs: ["STREIFE"],
    duration: 1,
    deadlineMinutes: 15,
    chain: [
      {
        id: "first",
        name: "Erster Hinweis",
        priority: 3,
        needs: ["STREIFE"],
        duration: 2,
        onFailure: "next",
        onSuccess: "next",
      },
      {
        id: "next",
        name: "Weiterer Hinweis",
        priority: 2,
        needs: ["KRIPO"],
        duration: 10,
      },
    ],
  };
  await page.locator("#templateJson").fill(JSON.stringify(template));
  await page
    .getByRole("button", { name: "Als neue Vorlage speichern" })
    .click();
  await expect(page.locator("#templateList")).toContainText(
    "Eigene Einsatzkette",
  );
  await page.locator('[data-tab="dispatch"]').click();
  await page.getByRole("button", { name: "8/12/24h-Schicht planen" }).click();
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await page.locator('[data-tab="editor"]').click();
  await page
    .locator(".listrow")
    .filter({ hasText: "Eigene Einsatzkette" })
    .getByRole("button", { name: "Auslösen", exact: true })
    .click();
  await advance(page, 16);
  expect(
    await page.evaluate(() =>
      (window as any).EL110.getState().incidents.some(
        (i: any) => i.name === "Weiterer Hinweis",
      ),
    ),
  ).toBe(true);
  await advance(page, 480);
  await page.getByRole("button", { name: "Nächsten Tag vorbereiten" }).click();
  expect(
    await page.evaluate(
      () =>
        (window as any).EL110.getState().units.find(
          (u: any) => u.regionId === "NI",
        ).training.remainingDays,
    ),
  ).toBe(1);
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await advance(page, 480);
  expect(
    await page.evaluate(
      () =>
        (window as any).EL110.getState().units.find(
          (u: any) => u.regionId === "NI",
        ).training,
    ),
  ).toBe(null);
  await page.reload();
  expect(
    await page.evaluate(
      () =>
        (window as any).EL110.getState().units.find(
          (u: any) => u.regionId === "NI",
        ).vehicle.speedKmh,
    ),
  ).toBe(75);
  expect(errors).toEqual([]);
});

test("hundred-object statewide map performance and persistent marker accounting", async ({
  page,
}) => {
  await setup(page, "sandbox", "NI");
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  const result = await page.evaluate(async () => {
    const app = (window as any).EL110,
      s = app.getState();
    s.autoCalls = false;
    const regional = s.units.filter((u: any) => u.regionId === "NI");
    const base = regional[0];
    for (let n = 0; n < 100; n++) {
      const unit = JSON.parse(JSON.stringify(base));
      unit.id = ++s.seq;
      unit.name = "Messflotte " + n;
      unit.vehicle.id = ++s.seq;
      for (const team of [unit.officers, ...unit.reserveOfficers])
        for (const o of team) o.id = ++s.seq;
      unit.position = [...regional[n % 32].position];
      s.units.push(unit);
    }
    for (let i = 0; i < 20; i++) (window as any).newIncident(1);
    const times: number[] = [];
    for (let i = 0; i < 60; i++) {
      const now = performance.now();
      app.map.update(s);
      times.push(performance.now() - now);
    }
    times.sort((a, b) => a - b);
    let frames = 0;
    const start = performance.now();
    await new Promise<void>((resolve) => {
      const tick = () => {
        frames++;
        if (performance.now() - start >= 1200) resolve();
        else requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    return {
      objects: s.units.length + s.incidents.length,
      medianUpdateMs: times[30],
      p95UpdateMs: times[57],
      browserAnimationFps: (frames * 1000) / (performance.now() - start),
      viewport: [innerWidth, innerHeight],
      userAgent: navigator.userAgent,
    };
  });
  const { writeFileSync } = await import("node:fs");
  writeFileSync(
    `${evidence}/browser-performance.json`,
    JSON.stringify(result, null, 2),
  );
  expect(result.objects).toBeGreaterThanOrEqual(150);
  expect(result.p95UpdateMs).toBeLessThan(100);
  await expect(page.locator(".map-legend")).toContainText("132 / 132");
});

test("career Hamburg first shift: three calls including P1, real dispatch and day-two management", async ({
  page,
}) => {
  const errors = await setup(page, "career", "HH", "early");
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Verteilte Notrufe auslösen" })
    .click();
  const assignments = await page.evaluate(() => {
    const s = (window as any).EL110.getState();
    return s.incidents.slice(0, 3).map((i: any) => ({
      name: i.name,
      uid: s.units.find(
        (u: any) =>
          u.regionId === "HH" &&
          u.type === "STREIFE" &&
          u.position[0] === i.position[0],
      ).id,
    }));
  });
  for (const pair of assignments) {
    if (!(await page.locator(".map-member").count()))
      await page.locator(".map-list-all").click();
    await page.locator(".map-member").filter({ hasText: pair.name }).click();
    await page.locator("#mapDispatch").selectOption(String(pair.uid));
    await page
      .locator("#mapDetail")
      .getByRole("button", { name: "Entsenden", exact: true })
      .click();
  }
  await advance(page, 30);
  expect(
    await page.evaluate(
      () => (window as any).EL110.getState().shiftStats.solved,
    ),
  ).toBeGreaterThanOrEqual(1);
  await advance(page, 480);
  await expect(page.locator("#modalBody")).toContainText("Tagesauswertung");
  await page.getByRole("button", { name: "Nächsten Tag vorbereiten" }).click();
  await expect(page.locator("#modalBody")).toContainText("TAG 2");
  expect(errors).toEqual([]);
});

test("dispatch selection survives an intervening live tick after focus leaves the selector", async ({
  page,
}) => {
  await setup(page, "sandbox", "NI");
  await page
    .getByRole("button", { name: "Schicht beginnen", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Verteilte Notrufe auslösen" })
    .click();
  await page.locator(".map-list-all").click();
  await page.getByRole("button", { name: /Notruf · Hannover.*P1/ }).click();
  const uid = await page.evaluate(
    () =>
      (window as any).EL110.getState().units.find(
        (u: any) => u.regionId === "NI" && u.type === "STREIFE",
      ).id,
  );
  await page.locator("#mapDispatch").selectOption(String(uid));
  await page
    .locator("#mapDispatch")
    .evaluate((el: HTMLSelectElement) => el.blur());
  await advance(page, 1);
  await expect(page.locator("#mapDispatch")).toHaveValue(String(uid));
  await page
    .locator("#mapDetail")
    .getByRole("button", { name: "Entsenden", exact: true })
    .click();
  expect(
    await page.evaluate(
      (id) =>
        (window as any).EL110.getState().units.find((u: any) => u.id === id)
          .incidentId,
      uid,
    ),
  ).not.toBe(null);
});
