/**
 * FootprintPrimitive - Displays order flow data (buy/sell volume) within each candle
 * Shows volume traded at each price level with color-coding for buy/sell pressure
 * 
 * Data format expected:
 * {
 *   time: number (unix timestamp),
 *   volumeByPrice: [
 *     { price: number, buyVolume: number, sellVolume: number }
 *   ]
 * }
 */

class FootprintPaneView {
    constructor(source) {
        this._source = source;
    }

    update() {
        // Called when data changes
    }

    renderer() {
        return this._source._renderer;
    }
}

class FootprintRenderer {
    constructor(source) {
        this._source = source;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._drawImpl(scope, target));
    }

    _drawImpl(scope, target) {
        const ctx = scope.context;
        
        const data = this._source._data;
        console.log('[Footprint] Draw called, data count:', data?.length);

        if (!data || data.length === 0) {
            console.log('[Footprint] No data to draw');
            return;
        }

        const chart = this._source._chart;
        const series = this._source._series;
        
        console.log('[Footprint] Chart/Series check:', { 
            hasChart: !!chart, 
            hasSeries: !!series,
            chartType: typeof chart,
            seriesType: typeof series
        });
        
        if (!chart || !series) {
            console.warn('[Footprint] Draw called but chart/series not available');
            return;
        }

        let drawnCount = 0;
        let skippedCount = 0;

        ctx.save();
        
        // Scale font size for bitmap coordinates
        const pixelRatio = scope.horizontalPixelRatio || 1;
        const fontSize = this._fontSize * pixelRatio;
        ctx.font = `${fontSize}px monospace`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';

        // Get visible time range
        const timeScale = chart.timeScale();
        
        console.log('[Footprint] Scale check:', { 
            hasTimeScale: !!timeScale, 
            hasSeries: !!series
        });
        
        if (!timeScale || !series) {
            console.log('[Footprint] No time scale or series available');
            ctx.restore();
            return;
        }

        const visibleRange = timeScale.getVisibleLogicalRange();
        
        console.log('[Footprint] Visible range check:', { 
            hasVisibleRange: !!visibleRange,
            visibleRange 
        });
        
        if (!visibleRange) {
            console.log('[Footprint] No visible range');
            ctx.restore();
            return;
        }

        console.log('[Footprint] ✅ Starting to draw', data.length, 'candles');

        // Calculate max volume for scaling volume bars
        let maxVolume = 0;
        data.forEach(candle => {
            if (candle.volume && candle.volume > maxVolume) {
                maxVolume = candle.volume;
            }
        });

        // Draw footprint data for each candle
        data.forEach((candleData, candleIndex) => {
            // Get candle X position
            const x = timeScale.timeToCoordinate(candleData.time);
            if (x === null || x === undefined) {
                skippedCount++;
                return;
            }
            
            // Scale X coordinate for bitmap
            const scaledX = x * pixelRatio;

            // Get bar spacing to determine available width
            const barSpacing = timeScale.options().barSpacing || 6;
            const candleWidth = barSpacing * 0.8 * pixelRatio;
            
            if (candleIndex === 0) {
                console.log('[Footprint] First candle:', {
                    time: candleData.time,
                    x: scaledX,
                    barSpacing,
                    candleWidth,
                    hasOHLC: !!(candleData.open && candleData.high && candleData.low && candleData.close),
                    volumeLevels: candleData.volumeByPrice?.length || 0
                });
            }

            // Draw candle body and wick first
            if (candleData.open && candleData.high && candleData.low && candleData.close) {
                const openY = series.priceToCoordinate(candleData.open);
                const highY = series.priceToCoordinate(candleData.high);
                const lowY = series.priceToCoordinate(candleData.low);
                const closeY = series.priceToCoordinate(candleData.close);
                
                if (openY !== null && highY !== null && lowY !== null && closeY !== null) {
                    const scaledOpenY = openY * pixelRatio;
                    const scaledHighY = highY * pixelRatio;
                    const scaledLowY = lowY * pixelRatio;
                    const scaledCloseY = closeY * pixelRatio;
                    
                    const isUp = candleData.close >= candleData.open;
                    const bodyColor = isUp ? 'rgba(34, 197, 94, 0.2)' : 'rgba(239, 68, 68, 0.2)';
                    const borderColor = isUp ? 'rgba(34, 197, 94, 0.8)' : 'rgba(239, 68, 68, 0.8)';
                    const wickColor = isUp ? 'rgba(34, 197, 94, 0.6)' : 'rgba(239, 68, 68, 0.6)';
                    
                    // Draw wick
                    ctx.strokeStyle = wickColor;
                    ctx.lineWidth = 1 * pixelRatio;
                    ctx.beginPath();
                    ctx.moveTo(scaledX, scaledHighY);
                    ctx.lineTo(scaledX, scaledLowY);
                    ctx.stroke();
                    
                    // Draw body
                    const bodyTop = Math.min(scaledOpenY, scaledCloseY);
                    const bodyBottom = Math.max(scaledOpenY, scaledCloseY);
                    const bodyHeight = Math.max(bodyBottom - bodyTop, 1 * pixelRatio); // Min 1px height
                    
                    // Fill body
                    ctx.fillStyle = bodyColor;
                    ctx.fillRect(
                        scaledX - candleWidth / 2,
                        bodyTop,
                        candleWidth,
                        bodyHeight
                    );
                    
                    // Draw body border
                    ctx.strokeStyle = borderColor;
                    ctx.lineWidth = 1 * pixelRatio;
                    ctx.strokeRect(
                        scaledX - candleWidth / 2,
                        bodyTop,
                        candleWidth,
                        bodyHeight
                    );
                    
                    // Draw volume bar below the candle
                    if (this._source._showVolumeBars && candleData.volume && maxVolume > 0) {
                        const viewportHeight = scope.bitmapSize.height;
                        const volumeBarMaxHeight = viewportHeight * 0.15; // 15% of chart height for volume
                        const volumeBarHeight = (candleData.volume / maxVolume) * volumeBarMaxHeight;
                        
                        // Position at bottom of chart
                        const volumeBarTop = viewportHeight - volumeBarHeight - (10 * pixelRatio); // 10px padding from bottom
                        
                        // Draw volume bar with transparency
                        const volumeColor = isUp 
                            ? 'rgba(34, 197, 94, 0.5)' 
                            : 'rgba(239, 68, 68, 0.5)';
                        ctx.fillStyle = volumeColor;
                        ctx.fillRect(
                            scaledX - candleWidth / 2,
                            volumeBarTop,
                            candleWidth,
                            volumeBarHeight
                        );
                        
                        // Draw volume bar border
                        const volumeBorderColor = isUp 
                            ? 'rgba(34, 197, 94, 0.8)' 
                            : 'rgba(239, 68, 68, 0.8)';
                        ctx.strokeStyle = volumeBorderColor;
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.strokeRect(
                            scaledX - candleWidth / 2,
                            volumeBarTop,
                            candleWidth,
                            volumeBarHeight
                        );
                    }
                }
            }

            // Skip volume profile if none available
            if (!candleData.volumeByPrice || candleData.volumeByPrice.length === 0) {
                return;
            }

            // Draw volume profile for this candle
            candleData.volumeByPrice.forEach((volumeData, levelIndex) => {
                const { price, buyVolume, sellVolume } = volumeData;
                
                // Skip if volume is too small
                if (buyVolume < this._source._minVolumeToShow && sellVolume < this._source._minVolumeToShow) {
                    return;
                }

                const y = series.priceToCoordinate(price);
                if (y === null || y === undefined) return;
                
                // Scale Y coordinate for bitmap
                const scaledY = y * pixelRatio;

                const delta = buyVolume - sellVolume;
                
                // Determine color based on delta
                let color;
                let bgColor;
                if (Math.abs(delta) < 0.001) {
                    color = this._source._neutralColor;
                    bgColor = 'rgba(156, 163, 175, 0.3)';
                } else if (delta > 0) {
                    color = this._source._buyColor;
                    bgColor = 'rgba(34, 197, 94, 0.3)';
                } else {
                    color = this._source._sellColor;
                    bgColor = 'rgba(239, 68, 68, 0.3)';
                }

                // Draw background highlight with stronger opacity
                const highlightWidth = Math.max(candleWidth * 0.9, 30 * pixelRatio);
                const highlightHeight = 14 * pixelRatio;
                ctx.fillStyle = bgColor;
                ctx.fillRect(
                    scaledX - highlightWidth / 2,
                    scaledY - highlightHeight / 2,
                    highlightWidth,
                    highlightHeight
                );

                // Draw text with white color for better visibility
                ctx.fillStyle = 'rgb(255, 255, 255)';
                ctx.font = `bold ${fontSize}px monospace`;
                
                let textToShow = '';
                if (this._source._showDelta) {
                    // Show delta
                    textToShow = delta >= 0 ? `+${delta.toFixed(3)}` : delta.toFixed(3);
                } else {
                    // Show buy vs sell volumes
                    const totalVolume = buyVolume + sellVolume;
                    textToShow = `${totalVolume.toFixed(3)}`;
                }
                
                ctx.fillText(textToShow, scaledX, scaledY);
                
                if (candleIndex === 0 && levelIndex === 0) {
                    console.log('[Footprint] Drawing first text:', {
                        text: textToShow,
                        x: scaledX,
                        y: scaledY,
                        price,
                        delta: delta.toFixed(5),
                        color
                    });
                }
                
                drawnCount++;
            });
            
            // Draw VAH, VAL, POC, and metrics for this candle
            if (this._source._showValueArea || this._source._showPOC || this._source._showMetrics) {
                const candleLeftX = scaledX - candleWidth / 2;
                const candleRightX = scaledX + candleWidth / 2;
                
                // Draw Value Area High (VAH)
                if (this._source._showValueArea && candleData.valueAreaHigh) {
                    const vahY = series.priceToCoordinate(candleData.valueAreaHigh);
                    if (vahY !== null && vahY !== undefined) {
                        const scaledVahY = vahY * pixelRatio;
                        ctx.strokeStyle = this._source._vahColor;
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.setLineDash([4 * pixelRatio, 2 * pixelRatio]);
                        ctx.beginPath();
                        ctx.moveTo(candleLeftX, scaledVahY);
                        ctx.lineTo(candleRightX, scaledVahY);
                        ctx.stroke();
                        ctx.setLineDash([]);
                        
                        // Label
                        ctx.fillStyle = this._source._vahColor;
                        ctx.font = `${7 * pixelRatio}px monospace`;
                        ctx.fillText('VAH', candleRightX + 15 * pixelRatio, scaledVahY);
                    }
                }
                
                // Draw Value Area Low (VAL)
                if (this._source._showValueArea && candleData.valueAreaLow) {
                    const valY = series.priceToCoordinate(candleData.valueAreaLow);
                    if (valY !== null && valY !== undefined) {
                        const scaledValY = valY * pixelRatio;
                        ctx.strokeStyle = this._source._valColor;
                        ctx.lineWidth = 1 * pixelRatio;
                        ctx.setLineDash([4 * pixelRatio, 2 * pixelRatio]);
                        ctx.beginPath();
                        ctx.moveTo(candleLeftX, scaledValY);
                        ctx.lineTo(candleRightX, scaledValY);
                        ctx.stroke();
                        ctx.setLineDash([]);
                        
                        // Label
                        ctx.fillStyle = this._source._valColor;
                        ctx.font = `${7 * pixelRatio}px monospace`;
                        ctx.fillText('VAL', candleRightX + 15 * pixelRatio, scaledValY);
                    }
                }
                
                // Draw Point of Control (POC)
                if (this._source._showPOC && candleData.pointOfControl) {
                    const pocY = series.priceToCoordinate(candleData.pointOfControl);
                    if (pocY !== null && pocY !== undefined) {
                        const scaledPocY = pocY * pixelRatio;
                        ctx.strokeStyle = this._source._pocColor;
                        ctx.lineWidth = 2 * pixelRatio;
                        ctx.beginPath();
                        ctx.moveTo(candleLeftX, scaledPocY);
                        ctx.lineTo(candleRightX, scaledPocY);
                        ctx.stroke();
                        
                        // Label
                        ctx.fillStyle = this._source._pocColor;
                        ctx.font = `bold ${8 * pixelRatio}px monospace`;
                        ctx.fillText('POC', candleRightX + 15 * pixelRatio, scaledPocY);
                    }
                }
                
                // Draw metrics (Delta and Total Volume) at top/bottom of candle
                if (this._source._showMetrics && barSpacing > 15) { // Only show if candles are wide enough
                    const topY = series.priceToCoordinate(candleData.high || 0);
                    const bottomY = series.priceToCoordinate(candleData.low || 0);
                    
                    if (topY !== null && bottomY !== null) {
                        const scaledTopY = topY * pixelRatio;
                        const scaledBottomY = bottomY * pixelRatio;
                        
                        // Delta at top
                        const delta = candleData.delta || 0;
                        const deltaColor = delta >= 0 ? this._source._buyColor : this._source._sellColor;
                        const deltaText = delta >= 0 ? `Δ+${delta.toFixed(2)}` : `Δ${delta.toFixed(2)}`;
                        
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
                        ctx.fillRect(
                            scaledX - 30 * pixelRatio,
                            scaledTopY - 20 * pixelRatio,
                            60 * pixelRatio,
                            12 * pixelRatio
                        );
                        ctx.fillStyle = deltaColor;
                        ctx.font = `bold ${8 * pixelRatio}px monospace`;
                        ctx.fillText(deltaText, scaledX, scaledTopY - 14 * pixelRatio);
                        
                        // Total Volume at bottom
                        const totalVol = candleData.volume || 0;
                        const volText = `${totalVol.toFixed(2)}`;
                        
                        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
                        ctx.fillRect(
                            scaledX - 30 * pixelRatio,
                            scaledBottomY + 8 * pixelRatio,
                            60 * pixelRatio,
                            12 * pixelRatio
                        );
                        ctx.fillStyle = 'rgb(156, 163, 175)';
                        ctx.font = `${7 * pixelRatio}px monospace`;
                        ctx.fillText(volText, scaledX, scaledBottomY + 14 * pixelRatio);
                    }
                }
            }
        });

        ctx.restore();
        
        console.log('[Footprint] ✅ COMPLETE: Drew', drawnCount, 'price levels,', skippedCount, 'skipped, across', data.length, 'candles');
    }
}

export class FootprintPrimitive {
    constructor(chart, series) {
        this._chart = chart;
        this._series = series;
        this._data = [];
        this._fontSize = 8;
        this._buyColor = 'rgb(34, 197, 94)'; // green
        this._sellColor = 'rgb(239, 68, 68)'; // red
        this._neutralColor = 'rgb(156, 163, 175)'; // gray
        this._vahColor = 'rgba(251, 191, 36, 0.6)'; // yellow for VAH
        this._valColor = 'rgba(251, 191, 36, 0.6)'; // yellow for VAL
        this._pocColor = 'rgba(34, 211, 238, 0.8)'; // cyan for POC
        this._showDelta = true;
        this._showValueArea = true; // Show VAH/VAL lines
        this._showPOC = true; // Show Point of Control
        this._showMetrics = true; // Show delta/volume text
        this._showVolumeBars = true; // Show volume bars at bottom
        this._minVolumeToShow = 0.001; // Don't show very small volumes
        this._renderer = new FootprintRenderer(this);
        this._paneViews = [new FootprintPaneView(this)];
        console.log('[Footprint] Primitive constructed with chart/series');
    }

    updateData(data) {
        this._data = data;
        console.log('[Footprint] Data updated:', data?.length, 'candles');
        if (data && data.length > 0) {
            console.log('[Footprint] Sample candle data:', {
                time: data[0].time,
                priceCount: data[0].volumeByPrice?.length || 0,
                firstPrice: data[0].volumeByPrice?.[0]
            });
        }
        this.updateAllViews();
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    setShowDelta(showDelta) {
        this._showDelta = showDelta;
    }
    
    setShowValueArea(show) {
        this._showValueArea = show;
    }
    
    setShowPOC(show) {
        this._showPOC = show;
    }
    
    setShowMetrics(show) {
        this._showMetrics = show;
    }
    
    setShowVolumeBars(show) {
        this._showVolumeBars = show;
    }
}

/**
 * Volume Profile Primitive - Shows horizontal volume bars at price levels
 * Better for showing overall volume distribution
 */
export class VolumeProfilePrimitive {
    constructor() {
        this._data = [];
        this._buyColor = 'rgba(34, 197, 94, 0.6)';
        this._sellColor = 'rgba(239, 68, 68, 0.6)';
        this._maxBarWidth = 100; // pixels
    }

    updateData(data) {
        this._data = data;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._drawImpl(scope, target));
    }

    _drawImpl(scope, target) {
        const ctx = scope.context;

        if (!this._data || this._data.length === 0) {
            return;
        }

        const chart = target.chart;
        const series = target.series;
        
        if (!chart || !series) return;

        const timeScale = chart.timeScale();
        const priceScale = series.priceScale();
        
        if (!timeScale || !priceScale) return;

        ctx.save();

        // Aggregate all volume data
        const volumeAtPrice = {};
        let maxVolume = 0;

        this._data.forEach(candleData => {
            if (!candleData.volumeByPrice) return;
            
            candleData.volumeByPrice.forEach(({ price, buyVolume, sellVolume }) => {
                const priceKey = price.toFixed(2);
                if (!volumeAtPrice[priceKey]) {
                    volumeAtPrice[priceKey] = { buy: 0, sell: 0 };
                }
                volumeAtPrice[priceKey].buy += buyVolume;
                volumeAtPrice[priceKey].sell += sellVolume;
                
                const total = volumeAtPrice[priceKey].buy + volumeAtPrice[priceKey].sell;
                maxVolume = Math.max(maxVolume, total);
            });
        });

        // Draw volume profile bars
        Object.keys(volumeAtPrice).forEach(priceKey => {
            const price = parseFloat(priceKey);
            const { buy, sell } = volumeAtPrice[priceKey];
            const total = buy + sell;
            
            const y = priceScale.priceToCoordinate(price);
            if (y === null || y === undefined) return;

            const barWidth = (total / maxVolume) * this._maxBarWidth;
            const barHeight = 3;

            // Draw sell volume (red)
            const sellWidth = (sell / total) * barWidth;
            ctx.fillStyle = this._sellColor;
            ctx.fillRect(scope.bitmapSize.width - barWidth, y - barHeight / 2, sellWidth, barHeight);

            // Draw buy volume (green)
            const buyWidth = (buy / total) * barWidth;
            ctx.fillStyle = this._buyColor;
            ctx.fillRect(scope.bitmapSize.width - barWidth + sellWidth, y - barHeight / 2, buyWidth, barHeight);
        });

        ctx.restore();
    }
}

