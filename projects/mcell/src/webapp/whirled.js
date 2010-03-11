if (!whirled) {
    var whirled = {};
}

whirled.addCharts = function () {
    eval(bedrock.include({
            'bedrock.util': ['log'],
            'bedrock.iter': ['each'],
            'bedrock.collections': ['List','Dict'],
            'panopticon.chart': ['addChart', 'init', 'StackedBarChart',
                                 'SelfContainedEventChart'],
            'panopticon.ui': ['CheckBoxes', 'RadioButtons'],
        }));

    return function() {
        addChart("economy", "exchange_rate", "Exchange Rate", function () {
            var options = {
                xaxis: { mode: "time", minTickSize: [1, "day"]}
            };
            var chart = new SelfContainedEventChart(
                "DailyExchangeRate", function (ev, collector) {
                    collector.assume("Exchange Rate").add(
                        [ev.date, ev.rate]);
                }, options, "date");
            return chart;
        });

        addChart("economy", "Earnings", "Earnings", function () {
            var actionNames = new List([
                [1, "Games"], [3, "Purchases"], [34, "Payouts"], [40, "Badges"],
                [50, "Bars Purchased"], [51, "Payouts"], [54, "Bling to Bars"],
                [55, "Cashed Out"]
            ]);
            var actions = new CheckBoxes("Actions", "actions", actionNames);
            var currency = new RadioButtons("Currency", "currency", ["coins", "bars", "bling" ]);
            function valueExtractor (event, name) {
                if (currency.value != event.currency || !actions.has(name)) {
                    return 0;
                }
                var earned = event["earned:" + name];
                return (earned > 0) ? earned : 0;
            }
            return new StackedBarChart(
                "DailyTransactions", actionNames, valueExtractor, {controls:[actions,currency]});
        });

        addChart("funnel", "login_count", "Daily Logins", function () {
            var sources = new CheckBoxes("Sources", "sources", new List([
                "Guests", "Registered", "Visitors", "Total" ]));
            var group = new RadioButtons("Group", "group", ["web", "embed" ]);
            var options = {
                xaxis: { mode: "time", minTickSize: [1, "hour"]},
                controls: [ sources, group ]
            };
            return new SelfContainedEventChart(
                "DailyLoginCount", function (ev, collector) {
                    if ((group.value == "embed") ^ ev.embed) {
                        return;
                    }
                    if (sources.has("Guests")) {
                        collector.assume("Guests").add([ev.timestamp, ev.uniqueGuests ]);
                    }
                    if (sources.has("Registered")) {
                        collector.assume("Registered").add([ev.timestamp, ev.uniquePlayers ]);
                    }
                    if (sources.has("Visitors")) {
                        collector.assume("Visitors").add([ev.timestamp, ev.uniqueVisitors ]);
                    }
                    if (sources.has("Total")) {
                        collector.assume("Total").add([ev.timestamp, ev.totalPlayers ]);
                    }
                }, options);
        });

        addChart("funnel", "funnel_web", "Conversion and Retention", function () {
            var sourceNames = new List([
                ["lost", "Lost"],
                ["played", "Played"],
                ["converted", "Converted"],
                ["returned", "Returned"],
                ["retained", "Retained"]
            ]);
            var sources = new CheckBoxes("Sources", "sources", sourceNames);
            var group = new RadioButtons("Group", "group", ["web", "embed" ]);
            function valueExtractor (event, name) {
                return (group.value == event.group && sources.has(name)) ?
                    (event[name] || 0) : 0;
            }
            var chart = new StackedBarChart(
                "DailyVisitorFutures", sourceNames, valueExtractor, {controls:[sources, group]});
            chart.extractKey = function (ev) { return ev.date; }
            return chart;
        });
    }
}();

